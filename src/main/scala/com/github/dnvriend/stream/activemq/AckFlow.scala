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
package activemq

import akka.{Done, NotUsed}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.{ExecutionContext, Future}

object AckFlow {

  /**
   * Transform this stream by applying the given function to each of the elements
   * as they pass through this processing step, acking each message.
   */
  def map[A, B](f: A ⇒ B): Flow[AckUTup[A], AckUTup[B], NotUsed] = Flow[AckUTup[A]].map {
    case (p, a) ⇒
      try {
        val out = p → f(a)
        if (!p.isCompleted) p.success(())
        out
      } catch {
        case cause: Throwable ⇒
          if (!p.isCompleted) p.failure(cause)
          throw cause
      }
  }

  /**
   * Transform this stream by applying the given function to each of the elements
   * as they pass through this processing step, acking each message.
   */
  def mapAsync[A, B](qos: Int)(f: A ⇒ Future[B])(implicit ec: ExecutionContext): Flow[AckUTup[A], AckUTup[B], NotUsed] = Flow[AckUTup[A]].mapAsync(qos) {
    case (p, a) ⇒ f(a).map { b ⇒
      if (!p.isCompleted) p.success(())
      p → b
    }.recover {
      case t: Throwable ⇒
        if (!p.isCompleted) p.failure(t)
        throw t
    }
  }

  /**
   * Only pass on those elements that satisfy the given predicate, acking each message.
   */
  def filter[A](predicate: A ⇒ Boolean): Flow[AckUTup[A], AckUTup[A], NotUsed] = Flow[AckUTup[A]].filter {
    case (p, a) ⇒
      try {
        val bool = predicate(a)
        if (!p.isCompleted) p.success(())
        bool
      } catch {
        case t: Throwable ⇒
          if (!p.isCompleted) p.failure(t)
          throw t
      }
  }
}
