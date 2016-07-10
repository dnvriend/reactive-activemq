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

import akka.NotUsed
import akka.actor.{ ActorLogging, ActorSystem, Props }
import akka.event.LoggingReceive
import akka.persistence.query.EventEnvelope
import akka.persistence.{ PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer }
import akka.stream._
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.integration.activemq.AckBidiFlow
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Sink, Source }
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.util.Failure

object ResumableQuery {
  def apply[A](
    queryName: String,
    query: Long ⇒ Source[EventEnvelope, NotUsed],
    snapshotInterval: Option[Long] = Some(250),
    matSink: Sink[Any, A] = Sink.ignore,
    journalPluginId: String = "",
    snapshotPluginId: String = ""
  )(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem): Flow[Any, Any, A] = {

    val source = Source.actorPublisher[(Long, Any)](Props(new ResumableQueryPublisher(queryName, query, journalPluginId, snapshotPluginId)))
    val sink = Flow[(Long, Any)].map(_._1).mapAsync(1) { offset ⇒
      import akka.pattern.ask
      import scala.concurrent.duration._
      implicit val timeout = Timeout(10.seconds)
      val writer = system.actorOf(Props(new ResumableQueryWriter(queryName, snapshotInterval, journalPluginId, snapshotPluginId)))
      (writer ? offset).map(_ ⇒ ())
    }.to(Sink.ignore)

    Flow.fromGraph(GraphDSL.create(source, sink, matSink)((_, _, matSink) ⇒ matSink) { implicit b ⇒ (src, snk, ignr) ⇒
      import GraphDSL.Implicits._

      val bidi = b.add(AckBidiFlow[Long, Any, Any]())
      val bcast = b.add(Broadcast[(Long, Any)](2, eagerCancel = false))

      src ~> bidi.in1
      bidi.out2 ~> bcast.in
      bcast ~> snk
      bcast ~> ignr

      FlowShape(bidi.in2, bidi.out1)
    })
  }
}

object ResumableQueryPublisher {
  final case class LatestOffset(offset: Long)
}

private[persistence] class ResumableQueryPublisher(queryName: String, query: Long ⇒ Source[EventEnvelope, NotUsed], override val journalPluginId: String, override val snapshotPluginId: String)(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem) extends PersistentActor with ActorPublisher[(Long, Any)] with DeliveryBuffer[(Long, Any)] with ActorLogging {
  import ResumableQueryPublisher._
  override val persistenceId: String = queryName
  var latestOffset: Long = 0L
  override val receiveRecover: Receive = {
    case SnapshotOffer(_, offset: Long) ⇒ latestOffset = offset
    case LatestOffset(offset)           ⇒ latestOffset = offset
    case RecoveryCompleted ⇒
      log.debug("Query: {} is recovering from: {}", queryName, latestOffset)
      query(latestOffset).runForeach(self ! _)
  }

  override val receiveCommand: Receive = LoggingReceive {
    case EventEnvelope(offset, _, _, event) ⇒
      buf ++= Option(offset → event); deliverBuf()
    case Request(req) ⇒ deliverBuf()
    case Cancel       ⇒ context.stop(self)
  }
}

private[persistence] trait DeliveryBuffer[T] {
  _: ActorPublisher[T] ⇒

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

private[persistence] class ResumableQueryWriter(queryName: String, snapshotInterval: Option[Long] = None, override val journalPluginId: String, override val snapshotPluginId: String)(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem) extends PersistentActor with ActorLogging {
  override val recovery: Recovery = Recovery.none
  override val persistenceId: String = queryName
  override val receiveRecover: Receive = PartialFunction.empty

  override val receiveCommand: Receive = LoggingReceive {
    case offset: Long ⇒
      log.debug("Query: {} is saving offset: {}", queryName, offset)
      persist(ResumableQueryPublisher.LatestOffset(offset)) { _ ⇒
        snapshotInterval.foreach { interval ⇒
          if (lastSequenceNr != 0L && lastSequenceNr % interval == 0)
            saveSnapshot(offset)
        }
        sender() ! akka.actor.Status.Success("")
      }
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
