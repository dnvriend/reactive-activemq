/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.dnvriend.stream
package persistence

import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.event.LoggingReceive
import akka.persistence.journal.Tagged
import akka.persistence.{ PersistentActor, Recovery }
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.{ ActorSubscriber, OneByOneRequestStrategy, RequestStrategy }
import akka.stream.scaladsl.Sink

object AckJournal {
  def empty(a: Any): Set[String] = Set.empty[String]

  def apply[A](journalName: String, tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Sink[AckTup[A], ActorRef] =
    sink(journalName, tags, journalPluginId)

  def sink[A](journalName: String, tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Sink[AckTup[A], ActorRef] =
    Sink.actorSubscriber[AckTup[A]](Props(new AckJournalActorSubscriber[A](journalName, tags, journalPluginId)))
}

private[persistence] class AckJournalActorSubscriber[A](journalName: String, tags: Any ⇒ Set[String], override val journalPluginId: String) extends ActorSubscriber with PersistentActor with ActorLogging {
  override protected val requestStrategy: RequestStrategy = OneByOneRequestStrategy
  override val recovery: Recovery = Recovery.none // disable recovery of both events and snapshots
  override val persistenceId: String = journalName

  override def receiveRecover: Receive = PartialFunction.empty

  private var previousMessage: AckTup[A] = _
  private def promise = previousMessage._1
  private def payload = previousMessage._2

  override def receiveCommand: Receive = LoggingReceive {
    case OnNext(msg) ⇒
      previousMessage = msg.asInstanceOf[AckTup[A]]
      val evaluatedTags = tags(payload)
      val msgToPersist = if (evaluatedTags.isEmpty) payload else Tagged(payload, evaluatedTags)
      persist(msgToPersist) { _ ⇒
        if (!promise.isCompleted) promise.success(())
        request(1)
      }
    case OnComplete ⇒
      log.warning("Receiving onComplete, stopping AckJournalSink for journal: {} using journalPluginId: {}", journalName, journalPluginId)
      if (!promise.isCompleted) promise.success(())
      context.stop(self)

    case OnError(cause) ⇒
      log.error(cause, "Receiving onError, stopping AckJournalSink for journal: {} using journalPluginId: {}", journalName, journalPluginId)
      if (!promise.isCompleted) promise.failure(cause)
      context.stop(self)
  }

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    log.error(cause, "AckJournalSync, persist failure for event: {} and sequenceNr: {}", event, seqNr)
    if (!promise.isCompleted) promise.failure(cause)
  }

  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
    log.error(cause, "AckJournalSync, persist rejected for event: {} and sequenceNr: {}", event, seqNr)
    if (!promise.isCompleted) promise.failure(cause)
  }
}
