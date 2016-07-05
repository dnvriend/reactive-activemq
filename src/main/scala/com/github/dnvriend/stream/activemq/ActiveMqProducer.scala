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

import akka.actor.{ ActorRef, ActorSystem }
import akka.camel.{ CamelExtension, CamelMessage }
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.{ Done, NotUsed }
import com.github.dnvriend.stream.activemq.extension.ActiveMqExtension
import com.github.dnvriend.stream.camel.CamelActorPublisher

import scala.collection.JavaConversions._
import scala.concurrent.{ ExecutionContext, Future }

object ActiveMqProducer {
  /**
   * Creates a flow that produces messages to a configured ActiveMq producer until upstream terminates.
   */
  def flow[T](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[T, CamelMessage]): Flow[T, T, NotUsed] = {
    val template = CamelExtension(system).template
    Flow[T].mapAsync(qos) {
      case payload ⇒
        Future {
          val camelMessage = builder.build(payload)
          val uri = ActiveMqExtension(system).producerEndpointUri(producerName)
          template.sendBodyAndHeaders(uri, camelMessage.body, camelMessage.headers.mapValues(_.asInstanceOf[AnyRef]))
          payload
        }
    }
  }

  /**
   * Creates a sink that produces messages to a configured ActiveMq producer until upstream terminates.
   */
  def sink[T](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[T, CamelMessage]): Sink[T, Future[Done]] =
    flow(producerName, qos).toMat(Sink.ignore)(Keep.right)

  /**
   * Creates a sink that produces messages to a configured ActiveMq producer until upstream terminates.
   */
  def apply[T](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[T, CamelMessage]): Sink[T, Future[Done]] =
    sink(producerName, qos)
}
