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
import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.persistence.query.scaladsl.{ EventsByPersistenceIdQuery, EventsByTagQuery, ReadJournal }
import akka.stream.Materializer
import akka.stream.actor._
import akka.stream.scaladsl.{ Sink, Source }

import scala.concurrent.{ ExecutionContext, Future, Promise }

object ResumableQuerySource {
  type ResumableQueries = ReadJournal with EventsByPersistenceIdQuery with EventsByTagQuery
  case object Completed
}

class ResumableQuerySource(queryName: String, readJournalId: String, override val journalPluginId: String = "", override val snapshotPluginId: String = "")(implicit mat: Materializer, ec: ExecutionContext)
    extends PersistentActor
    with ActorPublisher[AckTup[EventEnvelope]]
    with ActorSubscriber
    with ActorLogging {
  import ResumableQuerySource._

  override protected def requestStrategy: RequestStrategy = OneByOneRequestStrategy

  override val persistenceId: String = queryName

  var latestOffset: Long = 0L

  var promises = Vector.empty[(Promise[Unit], Future[Unit])]

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, offset: Long) ⇒ latestOffset = offset
    case offset: Long                   ⇒ latestOffset = if (offset >= latestOffset) offset else latestOffset
    case RecoveryCompleted ⇒
      queryWithOffset(latestOffset, PersistenceQuery(context.system)
        .readJournalFor(readJournalId).asInstanceOf[ResumableQueries])
        .runWith(Sink.actorRef(self, Completed))
  }

  def queryWithOffset(latestOffset: Long, journal: ResumableQueries): Source[EventEnvelope, NotUsed] =
    Source.empty

  override def receiveCommand: Receive = LoggingReceive {
    case msg @ EventEnvelope(offset, _, _, _) ⇒
      val p = Promise[Unit]()
      val f = p.future
      f.onSuccess {
        case _ ⇒
          persist(offset) { _ ⇒
            log.debug("Event: {} persisted", offset)
            if (lastSequenceNr != 0 && lastSequenceNr % 500 == 0) {
              log.debug("Snapshot: {} persisted, offset")
              saveSnapshot(latestOffset)
            }
          }
      }
      f.onFailure {
        case cause: Throwable ⇒
          log.error(cause, "The query has failed, stopping ResumableQuerySource")
          context.stop(self)
      }

      promises = promises.filterNot(_._1.isCompleted) ++ Option(p → f)
      onNext((p, msg))

    case Completed ⇒
      log.warning("The query has completed, stopping ResumableQuerySource")
      context.stop(self)
  }
}
