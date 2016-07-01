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

package com.github.dnvriend.stream.activemq

import akka.actor.{ ActorRef, ActorSystem }
import akka.camel.CamelMessage
import akka.stream._
import akka.stream.scaladsl.Source
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import com.github.dnvriend.stream.activemq.extension.ActiveMqExtension
import com.github.dnvriend.stream.camel.{ CamelActorPublisher, MessageExtractor }

import scala.concurrent.{ ExecutionContext, Future, Promise }

object ActiveMqSource {
  def apply[A](consumerName: String)(implicit ec: ExecutionContext, system: ActorSystem, extractor: MessageExtractor[CamelMessage, A]): Source[AckTup[A], ActorRef] =
    CamelActorPublisher.fromEndpointUriWithExtractor[A](ActiveMqExtension(system).consumerEndpointUri(consumerName)).via(new AckedFlow)
}

private class AckedFlow[A](implicit ec: ExecutionContext) extends GraphStage[FlowShape[(ActorRef, A), AckTup[A]]] {
  val in = Inlet[(ActorRef, A)]("AckedFlow.in")
  val out = Outlet[AckTup[A]]("AckedFlow.out")

  override val shape: FlowShape[(ActorRef, A), AckTup[A]] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    var promises = Vector.empty[(Promise[Unit], Future[Unit])]
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val (ref, b) = grab(in)
        val p = Promise[Unit]()
        val f = p.future
        f.onSuccess {
          case _ ⇒
            ref ! akka.camel.Ack
        }
        f.onFailure {
          case cause: Throwable ⇒
            ref ! akka.actor.Status.Failure(cause)
        }
        promises = promises.filterNot(_._1.isCompleted) ++ Option(p → f)
        push(out, p → b)
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}
