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
import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.pattern.{ PromiseActorRef, ask }
import akka.persistence.JournalProtocol.{ WriteMessagesSuccessful, _ }
import akka.persistence._
import akka.persistence.journal.Tagged
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.testkit.TestProbe
import akka.util.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }

/**
 * A [[akka.persistence.stream.Journal]] component is responsible for writing optionally tagged messages into the akka-persistence-journal.
 */
object Journal {

  /**
   * Returns a [[akka.stream.scaladsl.Flow]] that writes messages to a configured akka-persistence-journal
   */
  def apply[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = "")(implicit system: ActorSystem, ec: ExecutionContext, mat: Materializer): Flow[A, A, NotUsed] =
    flow(tags, journalPluginId)

  /**
   * Returns a [[akka.stream.scaladsl.Flow]] that writes messages to a configured akka-persistence-journal
   */
  def flow[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = "")(implicit system: ActorSystem, ec: ExecutionContext, mat: Materializer): Flow[A, A, NotUsed] =
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