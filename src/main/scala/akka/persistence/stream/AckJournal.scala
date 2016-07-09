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
import akka.actor.ActorSystem
import akka.stream.integration.AckUTup
import akka.stream.scaladsl.{ Flow, Source }

import scala.concurrent.ExecutionContext

/**
 * An [[akka.persistence.stream.AckJournal]] is responsible for writing optionally tagged messages into the akka-persistence-journal
 * and ack-ing each message.
 */
object AckJournal {
  private def empty(a: Any): Set[String] = Set.empty[String]

  /**
   * Returns an [[akka.stream.scaladsl.Sink]] that writes messages to akka-persistence and acks each message.
   */
  def apply[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = "")(implicit system: ActorSystem, ec: ExecutionContext): Flow[AckUTup[A], AckUTup[A], NotUsed] =
    flow(tags, journalPluginId)

  /**
   * Returns a [[akka.stream.scaladsl.Flow]] that writes messages to akka-persistence and acks each message.
   */
  def flow[A](tags: Any ⇒ Set[String] = empty, journalPluginId: String = "")(implicit system: ActorSystem, ec: ExecutionContext): Flow[AckUTup[A], AckUTup[A], NotUsed] =
    Flow[AckUTup[A]].flatMapConcat {
      case (promise, element) ⇒
        Source.single(element).via(Journal(tags, journalPluginId)).map { _ ⇒
          if (!promise.isCompleted) promise.success(())
          promise → element
        }.recover {
          case cause: Throwable ⇒
            if (!promise.isCompleted) promise.failure(cause)
            promise → element
        }
    }
}