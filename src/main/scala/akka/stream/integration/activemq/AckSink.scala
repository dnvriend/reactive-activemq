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

package akka.stream.integration
package activemq

import akka.Done
import akka.stream.scaladsl.{ Flow, Keep, Sink }

import scala.concurrent.Future

object AckSink {
  /**
   * A `Sink` that acks each message and keeps on collecting incoming elements until upstream terminates.
   */
  def seq[T]: Sink[AckUTup[T], Future[Seq[T]]] =
    Flow[AckTup[Unit, T]].map {
      case (p, a) =>
        if (!p.isCompleted) p.success(())
        a
    }.toMat(Sink.seq[T])(Keep.right).named("seqAckSink")

  /**
   * Creates a sink that acks each message and applies the given function with the received element until upstream terminates.
   */
  def foreach[A](f: A => Unit): Sink[AckUTup[A], Future[Done]] =
    Flow[AckUTup[A]].map {
      case (p, a) =>
        try {
          f(a)
          if (!p.isCompleted) p.success(())
        } catch {
          case cause: Throwable =>
            if (!p.isCompleted) p.failure(cause)
        }
    }.toMat(Sink.ignore)(Keep.right).named("foreachAckSink")

  /**
   * A sink that completes supplied promises with supplied values
   */
  def complete[A]: Sink[AckTup[A, A], Future[Done]] = {
    Flow[AckTup[A, A]].map {
      case (p, a) =>
        try {
          if (!p.isCompleted) p.success(a)
        } catch {
          case cause: Throwable =>
            if (!p.isCompleted) p.failure(cause)
        }
    }.toMat(Sink.ignore)(Keep.right).named("completeAckSink")
  }
}
