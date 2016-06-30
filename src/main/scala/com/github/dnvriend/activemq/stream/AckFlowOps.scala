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

import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.{ Done, NotUsed }

import scala.concurrent.{ ExecutionContext, Future }

object AckFlowOps {
  implicit class SourceOps[A, B](src: Source[AckTup[A], NotUsed]) {

    def fmapAck(f: A ⇒ B): Source[AckTup[B], NotUsed] = src.map {
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

    def fmap(f: A ⇒ B): Source[AckTup[B], NotUsed] = src.map {
      case (p, a) ⇒ p → f(a)
    }

    def fmapAsync(qos: Int)(f: A ⇒ Future[B])(implicit ec: ExecutionContext): Source[AckTup[B], NotUsed] = src.mapAsync(qos) {
      case (p, a) ⇒ f(a).map { b ⇒
        if (!p.isCompleted) p.success(())
        p → b
      }.recover {
        case t: Throwable ⇒
          if (!p.isCompleted) p.failure(t)
          throw t
      }
    }

    def runForeachAck(f: A ⇒ Unit)(implicit mat: Materializer): Future[Done] = src.runWith(AckSink.foreach(f))
  }
}
