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

import akka.actor.ActorSystem
import akka.event.{ LoggingAdapter, LoggingReceive }
import akka.persistence.query.scaladsl._
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.{ Done, NotUsed }

import scala.concurrent.{ ExecutionContext, Future }

object ResumableProjection {
  type JournalQueries = ReadJournal with EventsByPersistenceIdQuery with EventsByTagQuery
  final case class LatestOffset(offset: Long)
}

class ResumableProjection(projectionName: String, readJournalId: String)(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem, log: LoggingAdapter) extends PersistentActor {
  import ResumableProjection._
  override val persistenceId: String = projectionName

  var latestOffset: Long = 0L

  override def receiveRecover: Receive = LoggingReceive {
    case SnapshotOffer(_, offset: Long) ⇒ latestOffset = offset
    case LatestOffset(offset)           ⇒ latestOffset = offset
    case RecoveryCompleted              ⇒ queryWithOffset(latestOffset, PersistenceQuery(context.system).readJournalFor(readJournalId).asInstanceOf[JournalQueries])
  }

  override def receiveCommand: Receive = LoggingReceive {
    case msg @ EventEnvelope(offset, pid, sequenceNr, event) ⇒
      try {
        handleMessage(offset, pid, sequenceNr, event)
        latestOffset = offset
        persist(LatestOffset(offset)) { _ ⇒
          if (lastSequenceNr != 0 && lastSequenceNr % 500 == 0) saveSnapshot(latestOffset)
        }
      } catch {
        case t: Throwable ⇒ log.error(t, "could not handle message: {}", msg)
      }
  }

  /**
   * Will be called right after the recovery has been completed. The outcome is the latestOffset
   * that can be used to create a Query
   *
   * @param latestOffset
   * @param journal
   */
  def queryWithOffset(latestOffset: Long, journal: JournalQueries): Unit = ()

  def handleMessage(offset: Long, persistenceId: String, sequenceNr: Long, event: Any): Unit = ()

  implicit class RunQueryOps(src: Source[EventEnvelope, NotUsed]) {
    def run: Future[Done] = src.runForeach(self ! _)
  }
}

//object Foo extends App {
//
//  new ResumableProjection("table1", "jdbc-read-journal-voor-table-Messages") {
//    override def queryWithOffset(latestOffset: Long, journal: JournalQueries): Unit = {
//      journal.eventsByTag("tag1", latestOffset).run
//    }
//
//    override def handleMessage(offset: Long, persistenceId: String, sequenceNr: Long, event: Any): Unit = {
//      // do your synchronous stuff here
//    }
//  }
//}
