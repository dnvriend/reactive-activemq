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

import akka.NotUsed
import akka.actor.{ ActorLogging, ActorRef, ActorSystem, Props }
import akka.event.LoggingReceive
import akka.persistence.JournalProtocol._
import akka.persistence._
import akka.persistence.journal.Tagged
import akka.stream.Materializer
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.{ ActorSubscriber, OneByOneRequestStrategy, RequestStrategy }
import akka.stream.scaladsl.{ Flow, Sink }
import akka.testkit.TestProbe
import akka.util.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext
import scala.util.Failure

/**
 * A [[akka.persistence.stream.Journal]] component is responsible for writing optionally tagged messages into the akka-persistence-journal.
 */
object Journal {

  /**
   * Returns a [[akka.stream.scaladsl.Flow]] that writes messages to a configured akka-persistence-journal
   */
  def apply[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = "")(implicit system: ActorSystem, ec: ExecutionContext): Flow[A, A, NotUsed] =
    flow(tags, journalPluginId)

  /**
   * Returns an [[akka.stream.scaladsl.Sink]] that writes messages to the akka-persistence-journal.
   */
  def sink[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = ""): Sink[A, ActorRef] =
    Sink.actorSubscriber[A](Props(new JournalActorSubscriber[A](tags, journalPluginId)))

  def flow[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = "")(implicit system: ActorSystem, ec: ExecutionContext) = Flow[A].mapAsync(1) { element ⇒
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout = Timeout(10.seconds)
    val writer = system.actorOf(Props(new JournalActor(tags, journalPluginId)))
    (writer ? element).map(_ ⇒ element)
  }

  /**
   * Returns a [[akka.stream.scaladsl.Flow]] that writes messages to a configured akka-persistence-journal
   */
  def flowDirect[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = "")(implicit system: ActorSystem, ec: ExecutionContext, mat: Materializer): Flow[A, A, NotUsed] =
    Flow[A].map { payload ⇒
      val journal = Persistence(system).journalFor(journalPluginId)
      val tp = TestProbe()
      val xs: Seq[PersistentEnvelope] = Seq(AtomicWrite(createRepr(payload, tags(payload))))
      val cmd = WriteMessages(xs, tp.ref, 1)
      tp.send(journal, cmd)
      tp.expectMsgPF() {
        case WriteMessageFailure(msg, cause, _) ⇒ throw cause
        case WriteMessagesFailed(cause)         ⇒ throw cause
        case _                                  ⇒ payload
      }
    }

  private def empty(a: Any): Set[String] = Set.empty[String]

  private def randomId = UUID.randomUUID().toString

  private def createRepr(payload: Any, tags: Set[String])(implicit system: ActorSystem) = {
    val id = randomId
    PersistentRepr(
      payload = if (tags.isEmpty) payload else Tagged(payload, tags),
      sequenceNr = 1,
      persistenceId = "JournalWriter-" + id,
      writerUuid = id
    )
  }
}

private[persistence] class JournalActor(tags: Any ⇒ Set[String], override val journalPluginId: String) extends PersistentActor with ActorLogging {
  override val receiveRecover: Receive = PartialFunction.empty

  override val persistenceId: String = "JournalWriter-" + UUID.randomUUID().toString

  override val receiveCommand: Receive = LoggingReceive {
    case msg ⇒
      val evaluatedTags = tags(msg)
      val msgToPersist = if (evaluatedTags.isEmpty) msg else Tagged(msg, evaluatedTags)
      persist(msgToPersist)(_ ⇒ sender() ! akka.actor.Status.Success(""))
  }

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    super.onPersistFailure(cause, event, seqNr)
    sender() ! Failure(cause)
  }

  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
    super.onPersistRejected(cause, event, seqNr)
    sender() ! Failure(cause)
  }
}

private[persistence] class JournalActorSubscriber[A](tags: Any ⇒ Set[String], override val journalPluginId: String) extends ActorSubscriber with PersistentActor with ActorLogging {
  override protected val requestStrategy: RequestStrategy = OneByOneRequestStrategy
  override val recovery: Recovery = Recovery.none // disable recovery of both events and snapshots
  override val persistenceId: String = "JournalWriter-" + UUID.randomUUID().toString

  override val receiveRecover: Receive = PartialFunction.empty

  override val receiveCommand: Receive = LoggingReceive {
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

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    super.onPersistFailure(cause, event, seqNr)
    cancel()
    context stop self
  }

  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
    super.onPersistRejected(cause, event, seqNr)
    cancel()
    context stop self
  }
}