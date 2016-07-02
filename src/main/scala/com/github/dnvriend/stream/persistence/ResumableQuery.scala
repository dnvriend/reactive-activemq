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

import akka.actor.{ ActorLogging, ActorSystem, Props }
import akka.event.LoggingReceive
import akka.{ Done, NotUsed }
import akka.persistence.{ PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer }
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.stream._
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.{ ActorPublisher, ActorSubscriber, OneByOneRequestStrategy, RequestStrategy }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Keep, Sink, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import com.github.dnvriend.stream.persistence.ResumableQueryPublisher.Latestoffset

import scala.concurrent.{ ExecutionContext, Future }

object ResumableQuery {
  def apply(
    queryName: String,
    query: Long ⇒ Source[EventEnvelope, NotUsed],
    take: Long = Long.MaxValue,
    journalPluginId: String = "",
    snapshotPluginId: String = ""
  )(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem): Flow[EventEnvelope, EventEnvelope, Future[Done]] = {
    val resumableQuerySource = Source.actorPublisher[EventEnvelope](Props(new ResumableQueryPublisher(queryName, query, journalPluginId, snapshotPluginId)))
    val resumableQuerySink = Sink.actorSubscriber[EventEnvelope](Props(new ResumableQuerySubscriber(queryName, journalPluginId, snapshotPluginId)))
    val ignoreSink = Sink.ignore
    Flow.fromGraph(GraphDSL.create(resumableQuerySource, resumableQuerySink, ignoreSink)((_, _, ig) ⇒ ig) { implicit b ⇒ (source, sink, ignore) ⇒
      import GraphDSL.Implicits._
      val bcast = b.add(new Broadcast[EventEnvelope](2, false))
      val bidi = b.add(new ResumableQueryBidiFlow)
      source ~> bidi.in1
      bidi.out2 ~> Flow[EventEnvelope].take(take) ~> bcast.in
      bcast ~> sink
      bcast ~> ignore
      FlowShape(bidi.in2, bidi.out1)
    })
  }
}

private[persistence] class ResumableQueryBidiFlow extends GraphStage[BidiShape[EventEnvelope, EventEnvelope, EventEnvelope, EventEnvelope]] {
  val in1 = Inlet[EventEnvelope]("ResumableQueryBidiFlow.in1")
  val out1 = Outlet[EventEnvelope]("ResumableQueryBidiFlow.out1")
  val in2 = Inlet[EventEnvelope]("ResumableQueryBidiFlow.in2")
  val out2 = Outlet[EventEnvelope]("ResumableQueryBidiFlow.out2")

  override val shape: BidiShape[EventEnvelope, EventEnvelope, EventEnvelope, EventEnvelope] =
    BidiShape.of(in1, out1, in2, out2)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    setHandler(in1, new InHandler {
      override def onPush(): Unit = {
        push(out1, grab(in1))
      }
    })

    setHandler(out1, new OutHandler {
      override def onPull(): Unit = {
        pull(in1)
      }
    })

    setHandler(in2, new InHandler {
      override def onPush(): Unit = {
        push(out2, grab(in2))
      }
    })

    setHandler(out2, new OutHandler {
      override def onPull(): Unit = {
        pull(in2)
      }
    })
  }
}

object ResumableQueryPublisher {
  final case class Latestoffset(offset: Long)
}

private[persistence] class ResumableQueryPublisher(queryName: String, query: Long ⇒ Source[EventEnvelope, NotUsed], override val journalPluginId: String, override val snapshotPluginId: String)(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem) extends PersistentActor with ActorPublisher[EventEnvelope] with DeliveryBuffer[EventEnvelope] with ActorLogging {
  override val persistenceId: String = queryName
  var latestOffset: Long = 0L

  override val receiveRecover: Receive = LoggingReceive {
    case SnapshotOffer(_, offset: Long) ⇒
      log.debug(s"[pub]: recover from snapshot: $offset")
      latestOffset = offset
    case Latestoffset(offset) ⇒
      log.debug(s"[pub]: recover from event: $offset")
      latestOffset = offset

    case RecoveryCompleted ⇒
      log.debug(s"[pub] ===> Recovered to latestSeqNr: $lastSequenceNr and latestOffset: $latestOffset, running query")
      query(latestOffset).runForeach(self ! _)
  }

  override val receiveCommand: Receive = LoggingReceive {
    case msg: EventEnvelope ⇒
      buf ++= Option(msg); deliverBuf()
    case Request(req) ⇒ deliverBuf()
    case Cancel       ⇒ context.stop(self)
  }
}

private trait DeliveryBuffer[T] { _: ActorPublisher[T] ⇒

  var buf = Vector.empty[T]

  def deliverBuf(): Unit =
    if (buf.nonEmpty && totalDemand > 0) {
      if (buf.size == 1) {
        // optimize for this common case
        onNext(buf.head)
        buf = Vector.empty
      } else if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        use foreach onNext
      } else {
        buf foreach onNext
        buf = Vector.empty
      }
    }
}

private[persistence] class ResumableQuerySubscriber(queryName: String, override val journalPluginId: String, override val snapshotPluginId: String)(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem) extends PersistentActor with ActorSubscriber with ActorLogging {
  override protected def requestStrategy: RequestStrategy = OneByOneRequestStrategy
  override val recovery: Recovery = Recovery.none // disable recovery of both events and snapshots
  override val persistenceId: String = queryName
  override val receiveRecover: Receive = PartialFunction.empty

  override val receiveCommand: Receive = LoggingReceive {
    case OnNext(EventEnvelope(offset, _, _, _)) ⇒
      log.debug(s"[sub] ===> Persisting $offset")
      persist(ResumableQueryPublisher.Latestoffset(offset)) { msg ⇒
        log.debug(s"$msg Persisted")
      }
      request(1)

    case OnComplete ⇒
      println("Receiving onComplete, stopping AckJournalSink for query: {} using journalPluginId: {}", queryName, journalPluginId)

    case OnError(cause) ⇒
      println(cause, "Receiving onError, stopping AckJournalSink for query: {} using journalPluginId: {}", queryName, journalPluginId)

    case msg ⇒ println(msg)
  }
}
