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

import akka.NotUsed
import akka.actor.{ ActorLogging, ActorRef, ActorSystem, Props }
import akka.camel.{ CamelMessage, Consumer }
import akka.event.LoggingReceive
import akka.stream._
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.scaladsl.{ Keep, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import com.github.dnvriend.activemq.extension.ActiveMqExtension

import scala.concurrent.{ ExecutionContext, Future, Promise }

class CamelConsumer[A](val endpointUri: String)(implicit extractor: MessageExtractor[CamelMessage, A]) extends Consumer with ActorPublisher[(ActorRef, A)] with ActorLogging {
  override val autoAck: Boolean = false

  override def receive: Receive = LoggingReceive {
    case CamelMessage if totalDemand == 0 ⇒
      sender() ! akka.actor.Status.Failure(new IllegalStateException("No demand for new messages"))

    case msg: CamelMessage ⇒
      try {
        onNext((sender(), extractor.extract(msg)))
      } catch {
        case t: Throwable ⇒
          log.error(t, "Removing message from the broker because of error while extracting the message")
          sender() ! akka.camel.Ack
      }

    case Cancel ⇒ context.stop(self)
  }
}

object ActiveMqSource {
  def apply[A](name: String)(implicit ec: ExecutionContext, system: ActorSystem, extractor: MessageExtractor[CamelMessage, A]): Source[AckTup[A], NotUsed] =
    Source.actorPublisher[(ActorRef, A)](Props(new CamelConsumer[A](ActiveMqExtension(system).consumerFor(name))))
      .viaMat(new AckedFlow)(Keep.none)
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
