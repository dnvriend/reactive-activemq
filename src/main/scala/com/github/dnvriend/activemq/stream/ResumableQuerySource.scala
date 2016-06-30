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

package com.github.dnvriend.activemq.stream

import akka.NotUsed
import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.event.LoggingReceive
import akka.persistence.query.EventEnvelope
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.stream.Materializer
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor._
import akka.stream.scaladsl.{ Sink, Source }
import com.github.dnvriend.activemq.stream.ResumableQueryOffset.{ LatestOffset, SaveOffset }

import scala.concurrent.{ ExecutionContext, Future, Promise }

object ResumableQuerySource {
  def apply(queryName: String, readJournalId: String, journalPluginId: String = "", snapshotPluginId: String = "")(queryWithOffset: Long ⇒ Source[EventEnvelope, NotUsed] = (_: Long) ⇒ Source.empty)(implicit mat: Materializer, ec: ExecutionContext): Source[AckTup[EventEnvelope], ActorRef] =
    Source.actorPublisher[AckTup[EventEnvelope]](Props(new ResumableQuerySource(queryName, readJournalId, journalPluginId, snapshotPluginId)(queryWithOffset)))
}

class ResumableQueryForwarder(ref: ActorRef) extends ActorSubscriber {
  override protected def requestStrategy: RequestStrategy = OneByOneRequestStrategy

  override def receive: Receive = LoggingReceive {
    case msg: OnNext ⇒
      ref ! msg
      request(1)
    case msg @ OnComplete ⇒
      ref ! msg
      context.stop(self)
    case msg: OnError ⇒
      ref ! msg
      context.stop(self)
  }
}

object ResumableQueryOffset {
  final case class LatestOffset(offset: Long)
  final case class SaveOffset(offset: Long)
}
class ResumableQueryOffset(queryName: String, ref: ActorRef, override val journalPluginId: String, override val snapshotPluginId: String) extends PersistentActor with ActorLogging {
  override val persistenceId: String = queryName

  var latestOffset: Long = 0L

  override def receiveRecover: Receive = LoggingReceive {
    case SnapshotOffer(_, offset: Long) ⇒ latestOffset = offset
    case offset: Long                   ⇒ latestOffset = if (offset >= latestOffset) offset else latestOffset
    case RecoveryCompleted              ⇒ ref ! ResumableQueryOffset.LatestOffset(latestOffset)
  }

  override def receiveCommand: Receive = LoggingReceive {
    case SaveOffset(offset) ⇒
      log.debug("Persisting: {} => {}", offset, self.path)
      persist(offset) { offset ⇒
        log.debug("Persisted: {} => {}", offset, self.path)
      }
  }
}

class ResumableQuerySource(queryName: String, readJournalId: String, journalPluginId: String, snapshotPluginId: String)(queryWithOffset: Long ⇒ Source[EventEnvelope, NotUsed])(implicit mat: Materializer, ec: ExecutionContext)
    extends ActorPublisher[AckTup[EventEnvelope]]
    with ActorLogging {

  import ResumableQuerySource._

  val offsetActor = context.actorOf(Props(new ResumableQueryOffset(queryName, self, journalPluginId, snapshotPluginId)), queryName)

  var promises = Vector.empty[(Promise[Unit], Future[Unit])]

  override def receive: Receive = LoggingReceive {
    case OnNext(msg @ EventEnvelope(offset, _, _, _)) ⇒
      val p = Promise[Unit]()
      val f = p.future
      f.onSuccess {
        case _ ⇒
          val offsetToPersist = offset
          log.debug("SaveOffset msg: {}", offsetToPersist)
          offsetActor ! SaveOffset(offsetToPersist)
      }

      f.onFailure {
        case cause: Throwable ⇒
          log.error(cause, "Upstream has failed the Ack, stopping ResumableQuerySource)")
          onCompleteThenStop()
      }

      promises = promises.filterNot(_._1.isCompleted) ++ Option(p → f)
      onNext((p, msg))

    case Cancel ⇒
      log.warning("Upstream has canceled, stopping ResumableQuerySource")
      context stop self

    case OnComplete ⇒
      log.warning("The query has completed, stopping ResumableQuerySource")
      onCompleteThenStop()

    case OnError(cause) ⇒
      log.error(cause, "The query has failed, stopping ResumableQuerySource")
      onErrorThenStop(cause)

    case LatestOffset(offset) ⇒
      queryWithOffset(offset).runWith(Sink.actorSubscriber(Props(new ResumableQueryForwarder(self))))
  }
}
