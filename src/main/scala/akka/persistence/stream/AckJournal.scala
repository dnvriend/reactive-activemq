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

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.integration.AckUTup
import akka.stream.integration.activemq.{ AckSink, ActiveMqFlow }
import akka.stream.scaladsl._
import akka.util.Timeout
import akka.{ Done, NotUsed }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * The [[akka.persistence.stream.AckJournal]] is responsible for writing optionally tagged messages into the akka-persistence-journal
 * and ack-ing each message. The messages can optionally be preprocessed.
 */
object AckJournal {
  private def empty(a: Any): Set[String] = Set.empty[String]

  /**
   * Returns an [[akka.stream.scaladsl.Flow]] that writes messages to akka-persistence and acks each message.
   */
  def apply[S, T, M](source: Source[AckUTup[S], M], tags: Any => Set[String] = empty, preProcessor: Flow[S, T, NotUsed], journalPluginId: String = "")(implicit system: ActorSystem, ec: ExecutionContext, mat: Materializer, timeout: Timeout): Future[Done] =
    ActiveMqFlow.applyMat(source, AckSink.foreach[Unit](_ => ()))(Keep.right)
      .via(preProcessor)
      .via(Journal(tags, journalPluginId))
      .join(Flow[T].map(_ => ()))
      .run()
}