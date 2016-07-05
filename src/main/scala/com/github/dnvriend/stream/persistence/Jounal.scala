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

import akka.NotUsed
import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.event.LoggingReceive
import akka.persistence.journal.Tagged
import akka.persistence.{ PersistentActor, Recovery }
import akka.stream.FlowShape
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.{ ActorSubscriber, OneByOneRequestStrategy, RequestStrategy }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Sink }

object Journal {
  private def empty(a: Any): Set[String] = Set.empty[String]

  def apply[A](journalName: String, tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Sink[A, ActorRef] =
    sink(journalName, tags, journalPluginId)

  def sink[A](journalName: String, tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Sink[A, ActorRef] =
    Sink.actorSubscriber[A](Props(new JournalActorSubscriber[A](journalName, tags, journalPluginId)))

  def toA[A, B](journalName: String, tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Flow[(A, B), A, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._
      val broadcast = b.add(new Broadcast[(A, B)](2, false))
      val journalSink = sink[B](journalName, tags, journalPluginId)
      val outA = broadcast ~> Flow[(A, B)].map(_._1)
      broadcast ~> Flow[(A, B)].map(_._2) ~> journalSink
      FlowShape(broadcast.in, outA.outlet)
    })

  def toB[A, B](journalName: String, tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Flow[(A, B), B, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._
      val broadcast = b.add(new Broadcast[(A, B)](2, false))
      val journalSink = sink[B](journalName, tags, journalPluginId)
      val outB = broadcast ~> Flow[(A, B)].map(_._2)
      broadcast ~> Flow[(A, B)].map(_._2) ~> journalSink
      FlowShape(broadcast.in, outB.outlet)
    })
}

private[persistence] class JournalActorSubscriber[A](journalName: String, tags: Any ⇒ Set[String], override val journalPluginId: String) extends ActorSubscriber with PersistentActor with ActorLogging {
  override protected val requestStrategy: RequestStrategy = OneByOneRequestStrategy
  override val recovery: Recovery = Recovery.none // disable recovery of both events and snapshots
  override val persistenceId: String = journalName

  override def receiveRecover: Receive = PartialFunction.empty

  override def receiveCommand: Receive = LoggingReceive {
    case OnNext(msg) ⇒
      val evaluatedTags = tags(msg)
      val msgToPersist = if (evaluatedTags.isEmpty) msg else Tagged(msg, evaluatedTags)
      persist(msgToPersist)(_ ⇒ request(1))

    case OnComplete ⇒
      log.warning("Receiving onComplete, stopping AckJournalSink for journal: {} using journalPluginId: {}", journalName, journalPluginId)
      context.stop(self)

    case OnError(cause) ⇒
      log.error(cause, "Receiving onError, stopping AckJournalSink for journal: {} using journalPluginId: {}", journalName, journalPluginId)
      context.stop(self)
  }
}
