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

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.{ Done, NotUsed }

import scala.concurrent.{ ExecutionContext, Future }

object AckFlowOps {

  implicit class SourceOps[A, B](src: Source[AckTup[A, B], NotUsed]) {

    def fmapAck(f: B => A): Source[AckTup[A, A], NotUsed] = src.map {
      case (p, b) =>
        try {
          val a = f(b)
          val out = p -> a
          if (!p.isCompleted) p.success(a)
          out
        } catch {
          case cause: Throwable =>
            if (!p.isCompleted) p.failure(cause)
            throw cause
        }
    }

    def fmap[C](f: B => C): Source[AckTup[A, C], NotUsed] = src.map {
      case (p, a) => p -> f(a)
    }

    def fmapAsync(qos: Int)(f: B => Future[A])(implicit ec: ExecutionContext): Source[AckTup[A, A], NotUsed] = src.mapAsync(qos) {
      case (p, b) => f(b).map { a =>
        if (!p.isCompleted) p.success(a)
        p -> a
      }.recover {
        case t: Throwable =>
          if (!p.isCompleted) p.failure(t)
          throw t
      }
    }
  }

  implicit class SourceUnitOps[A](src: Source[AckTup[Unit, A], NotUsed]) {
    def runForeachAck(f: A => Unit)(implicit mat: Materializer): Future[Done] = src.runWith(AckSink.foreach(f))
  }
}