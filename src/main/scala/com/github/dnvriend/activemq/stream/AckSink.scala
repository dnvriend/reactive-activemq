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

import akka.Done
import akka.stream.scaladsl.{ Flow, Keep, Sink }

import scala.collection.immutable
import scala.concurrent.Future

object AckSink {
  /**
   * A `Sink` that acks each message and keeps on collecting incoming elements until upstream terminates.
   */
  def seq[T]: Sink[AckTup[T], Future[immutable.Seq[T]]] =
    Flow[AckTup[T]].map {
      case (p, a) ⇒
        if (!p.isCompleted) p.success(())
        a
    }.toMat(Sink.seq[T])(Keep.right)

  def foreach[A](f: A ⇒ Unit): Sink[AckTup[A], Future[Done]] =
    Flow[AckTup[A]].map {
      case (p, a) ⇒
        try {
          f(a)
          if (!p.isCompleted) p.success(())
        } catch {
          case cause: Throwable ⇒
            if (!p.isCompleted) p.failure(cause)
        }
    }.toMat(Sink.ignore)(Keep.right).named("foreachAckSink")
}
