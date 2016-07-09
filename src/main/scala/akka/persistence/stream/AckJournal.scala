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

package akka.persistence.stream

import java.util.UUID

import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.event.LoggingReceive
import akka.persistence.journal.Tagged
import akka.persistence.{ PersistentActor, Recovery }
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.{ ActorSubscriber, OneByOneRequestStrategy, RequestStrategy }
import akka.stream.integration.AckUTup
import akka.stream.scaladsl.Sink

/**
 * An [[akka.persistence.stream.AckJournal]] is responsible for writing optionally tagged messages into the akka-persistence-journal
 * and ack-ing each message.
 *
 * Each message should be tagged by the [[akka.persistence.stream.Journal]] component or using an [[akka.persistence.journal.EventAdapter]]
 * before any of the messages can be queried by [[akka.persistence.query.scaladsl.EventsByTagQuery]] or
 * [[akka.persistence.query.scaladsl.CurrentEventsByTagQuery]].
 *
 * Because the component is not an entity, it's name/persistence id is not relevant.
 * To be compliant with akka-persistence, each message will be stored in context of
 * a random ID prefixed with `JournalWriter-`.
 */
object AckJournal {
  private def empty(a: Any): Set[String] = Set.empty[String]

  /**
   * Returns an [[akka.stream.scaladsl.Sink]] that writes messages to the akka-persistence-journal and acks each message.
   */
  def apply[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Sink[AckUTup[A], ActorRef] =
    sink(tags, journalPluginId)

  /**
   * Returns an [[akka.stream.scaladsl.Sink]] that writes messages to the akka-persistence-journal and acks each message.
   */
  def sink[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Sink[AckUTup[A], ActorRef] =
    Sink.actorSubscriber[AckUTup[A]](Props(new AckJournalActorSubscriber[A](tags, journalPluginId)))
}

private[persistence] class AckJournalActorSubscriber[A](tags: Any ⇒ Set[String], override val journalPluginId: String) extends ActorSubscriber with PersistentActor with ActorLogging {
  override protected val requestStrategy: RequestStrategy = OneByOneRequestStrategy
  override val recovery: Recovery = Recovery.none // disable recovery of both events and snapshots
  override val persistenceId: String = "JournalWriter-" + UUID.randomUUID().toString

  override def receiveRecover: Receive = PartialFunction.empty

  private var previousMessage: AckUTup[A] = _
  private def promise = previousMessage._1
  private def payload = previousMessage._2

  override def receiveCommand: Receive = LoggingReceive {
    case OnNext(msg) ⇒
      previousMessage = msg.asInstanceOf[AckUTup[A]]
      val evaluatedTags = tags(payload)
      val msgToPersist = if (evaluatedTags.isEmpty) payload else Tagged(payload, evaluatedTags)
      persist(msgToPersist) { _ ⇒
        if (!promise.isCompleted) promise.success(())
        request(1)
      }
    case OnComplete ⇒
      log.warning("Receiving onComplete, stopping AckJournalSink for journal: {} using journalPluginId: {}", journalPluginId)
      if (!promise.isCompleted) promise.success(())
      context.stop(self)

    case OnError(cause) ⇒
      log.error(cause, "Receiving onError, stopping AckJournalSink for journal: {} using journalPluginId: {}", journalPluginId)
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
