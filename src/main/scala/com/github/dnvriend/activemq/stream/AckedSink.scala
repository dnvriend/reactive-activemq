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

import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler }
import akka.stream.{ Attributes, Inlet, SinkShape }

import scala.concurrent.Promise

abstract class AbstractAckedSink[A] extends GraphStage[SinkShape[AckTup[A]]] {
  val in: Inlet[AckTup[A]] = Inlet("input")

  def completePromise(p: Promise[Unit]): Unit = if (!p.isCompleted) p.success(())
  def failPromise(p: Promise[Unit], cause: Throwable): Unit = if (!p.isCompleted) p.failure(cause)
  def handle(p: Promise[Unit], a: A): Unit

  override val shape: SinkShape[AckTup[A]] = new SinkShape(in)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          println(s"==> Grabbing")
          val (p, a) = grab(in)
          handle(p, a)
          pull(in)
        }
      })
    }
}

class AckedSink[A] extends AbstractAckedSink[A] {
  override def handle(p: Promise[Unit], a: A): Unit =
    if (!p.isCompleted) completePromise(p)
}

class ForeachAckedSink[A](f: A ⇒ Unit) extends AbstractAckedSink[A] {
  override def handle(p: Promise[Unit], a: A): Unit =
    try {
      println("==> Completing in foreach")
      f(a)
      completePromise(p)
    } catch {
      case cause: Throwable ⇒
        println("===> Failing in Foreach")
        failPromise(p, cause)
    }
}
