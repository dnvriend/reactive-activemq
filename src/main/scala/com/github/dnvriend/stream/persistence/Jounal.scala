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

import java.util.UUID

import akka.NotUsed
import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.event.LoggingReceive
import akka.persistence.journal.Tagged
import akka.persistence.{ PersistentActor, Recovery }
import akka.stream.FlowShape
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.{ ActorSubscriber, OneByOneRequestStrategy, RequestStrategy }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Sink }

/**
 * A [[com.github.dnvriend.stream.persistence.Journal]] component is responsible for writing optionally tagged messages into the akka-persistence-journal.
 *
 * Each message should be tagged by the [[com.github.dnvriend.stream.persistence.Journal]] component or using an [[akka.persistence.journal.EventAdapter]]
 * before any of the messages can be queried by [[akka.persistence.query.scaladsl.EventsByTagQuery]] or
 * [[akka.persistence.query.scaladsl.CurrentEventsByTagQuery]].
 *
 * Because the component is not an entity, it's name/persistence id is not relevant.
 * To be compliant with akka-persistence, each message will be stored in context of
 * a random ID prefixed with `JournalWriter-`.
 */
object Journal {
  private def empty(a: Any): Set[String] = Set.empty[String]

  /**
   * Returns an [[akka.stream.scaladsl.Sink]] that writes messages to the akka-persistence-journal.
   */
  def apply[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Sink[A, ActorRef] =
    sink(tags, journalPluginId)

  /**
   * Returns an [[akka.stream.scaladsl.Sink]] that writes messages to the akka-persistence-journal.
   */
  def sink[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Sink[A, ActorRef] =
    Sink.actorSubscriber[A](Props(new JournalActorSubscriber[A](tags, journalPluginId)))

  /**
   * Returns a Flow that accepts (A, B) tuples and writes B's to the journal, returning A's
   */
  def toA[A, B](tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Flow[(A, B), A, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._
      val broadcast = b.add(new Broadcast[(A, B)](2, false))
      val journalSink = sink[B](tags, journalPluginId)
      val outA = broadcast ~> Flow[(A, B)].map(_._1)
      broadcast ~> Flow[(A, B)].map(_._2) ~> journalSink
      FlowShape(broadcast.in, outA.outlet)
    })

  /**
   * Returns a Flow that accepts (A, B) tuples and writes B's to the journal, returning B's
   */
  def toB[A, B](tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Flow[(A, B), B, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._
      val broadcast = b.add(new Broadcast[(A, B)](2, false))
      val journalSink = sink[B](tags, journalPluginId)
      val outB = broadcast ~> Flow[(A, B)].map(_._2)
      broadcast ~> Flow[(A, B)].map(_._2) ~> journalSink
      FlowShape(broadcast.in, outB.outlet)
    })
}

private[persistence] class JournalActorSubscriber[A](tags: Any ⇒ Set[String], override val journalPluginId: String) extends ActorSubscriber with PersistentActor with ActorLogging {
  override protected val requestStrategy: RequestStrategy = OneByOneRequestStrategy
  override val recovery: Recovery = Recovery.none // disable recovery of both events and snapshots
  override val persistenceId: String = "JournalWriter-" + UUID.randomUUID().toString

  override def receiveRecover: Receive = PartialFunction.empty

  override def receiveCommand: Receive = LoggingReceive {
    case OnNext(msg) ⇒
      val evaluatedTags = tags(msg)
      val msgToPersist = if (evaluatedTags.isEmpty) msg else Tagged(msg, evaluatedTags)
      persist(msgToPersist)(_ ⇒ request(1))

    case OnComplete ⇒
      log.warning("Receiving onComplete, stopping AckJournalSink for journal: {} using journalPluginId: {}", journalPluginId)
      context.stop(self)

    case OnError(cause) ⇒
      log.error(cause, "Receiving onError, stopping AckJournalSink for journal: {} using journalPluginId: {}", journalPluginId)
      context.stop(self)
  }
}