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

import akka.actor.ActorRef
import akka.camel.CamelMessage
import akka.stream._
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

import scala.concurrent.{ ExecutionContext, Future, Promise }

private[activemq] class AckedFlow[A, B](implicit ec: ExecutionContext) extends GraphStage[FlowShape[(ActorRef, B), AckTup[A, B]]] {
  val in = Inlet[(ActorRef, B)]("AckedFlow.in")
  val out = Outlet[AckTup[A, B]]("AckedFlow.out")

  override val shape: FlowShape[(ActorRef, B), AckTup[A, B]] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    var promises = Vector.empty[(Promise[A], Future[A])]
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val (ref, b) = grab(in)
        val p = Promise[A]()
        val eventualResponse = p.future
        eventualResponse.onSuccess(successResponse(ref))
        eventualResponse.onFailure {
          case cause: Throwable =>
            ref ! akka.actor.Status.Failure(cause)
        }
        promises = promises.filterNot(_._1.isCompleted) :+ (p → eventualResponse)
        push(out, p → b)
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }

  /* Extracted to allow overriding for request-response pattern */
  def successResponse(source: ActorRef): PartialFunction[A, Unit] = {
    case _ => source ! akka.camel.Ack
  }
}

class AckedResponseFlow[A, B](implicit ec: ExecutionContext, builder: MessageBuilder[A, CamelMessage]) extends AckedFlow[A, B] {
  override def successResponse(source: ActorRef): PartialFunction[A, Unit] = {
    case msg => source ! builder.build(msg)
  }
}
