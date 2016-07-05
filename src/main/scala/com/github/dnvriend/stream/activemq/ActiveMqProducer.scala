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

import akka.actor.ActorSystem
import akka.camel.{ CamelExtension, CamelMessage }
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.{ Done, NotUsed }
import com.github.dnvriend.stream.activemq.extension.ActiveMqExtension
import org.apache.camel.ProducerTemplate

import scala.collection.JavaConversions._
import scala.concurrent.{ ExecutionContext, Future }

object ActiveMqProducer {

  private def send[A](payload: A, producerName: String, endpointUri: String, producer: ProducerTemplate)(implicit ec: ExecutionContext, builder: MessageBuilder[A, CamelMessage]): Future[A] = Future {
    val msg = builder.build(payload)
    producer.sendBodyAndHeaders(endpointUri, msg.body, msg.headers.mapValues(_.asInstanceOf[AnyRef]))
    payload
  }

  /**
   * Creates a flow that produces messages to a configured ActiveMq producer until upstream terminates.
   */
  def flow[A](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[A, CamelMessage]): Flow[A, A, NotUsed] = {
    Flow[A].mapAsync(qos) { payload ⇒
      val producerTemplate = CamelExtension(system).template
      val endpointUri = ActiveMqExtension(system).producerEndpointUri(producerName)
      send(payload, producerName, endpointUri, producerTemplate)
    }
  }

  def toA[A, B](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[B, CamelMessage]): Flow[(A, B), A, NotUsed] = Flow[(A, B)].mapAsync(qos) {
    case (a, b) ⇒
      val producerTemplate = CamelExtension(system).template
      val endpointUri = ActiveMqExtension(system).producerEndpointUri(producerName)
      send(b, producerName, endpointUri, producerTemplate).map(_ ⇒ a)
  }

  def toB[A, B](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[B, CamelMessage]): Flow[(A, B), B, NotUsed] = Flow[(A, B)].mapAsync(qos) {
    case (a, b) ⇒
      val producerTemplate = CamelExtension(system).template
      val endpointUri = ActiveMqExtension(system).producerEndpointUri(producerName)
      send(b, producerName, endpointUri, producerTemplate)
  }

  /**
   * Creates a sink that produces messages to a configured ActiveMq producer until upstream terminates.
   */
  def sink[A](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[A, CamelMessage]): Sink[A, Future[Done]] =
    flow(producerName, qos).toMat(Sink.ignore)(Keep.right)

  /**
   * Creates a sink that produces messages to a configured ActiveMq producer until upstream terminates.
   */
  def apply[A](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[A, CamelMessage]): Sink[A, Future[Done]] =
    sink(producerName, qos)
}
