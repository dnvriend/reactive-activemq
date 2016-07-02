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

import akka.Done
import akka.actor.ActorSystem
import akka.camel.{ CamelExtension, CamelMessage }
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import com.github.dnvriend.stream.MessageBuilder
import com.github.dnvriend.stream.activemq.extension.ActiveMqExtension

import scala.collection.JavaConversions._
import scala.concurrent.{ ExecutionContext, Future }

object ActiveMqSink {
  def apply[T](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[T, CamelMessage]): Sink[T, Future[Done]] = {
    val template = CamelExtension(system).template
    Flow[T].mapAsync(qos) {
      case payload ⇒
        Future {
          val camelMessage = builder.build(payload)
          val uri = ActiveMqExtension(system).producerEndpointUri(producerName)
          template.sendBodyAndHeaders(uri, camelMessage.body, camelMessage.headers.mapValues(_.asInstanceOf[AnyRef]))
        }
    }.toMat(Sink.ignore)(Keep.right)
  }
}

object AckActiveMqSink {
  def apply[A](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem, builder: MessageBuilder[A, CamelMessage]): Sink[AckTup[A], Future[Done]] = {
    val template = CamelExtension(system).template
    Flow[AckTup[A]].mapAsync(qos) {
      case (p, payload) ⇒
        Future {
          val camelMessage = builder.build(payload)
          val uri = ActiveMqExtension(system).producerEndpointUri(producerName)
          template.sendBodyAndHeaders(uri, camelMessage.body, camelMessage.headers.mapValues(_.asInstanceOf[AnyRef]))
        }.map { _ ⇒ if (!p.isCompleted) p.success(()) }.recover { case cause: Throwable ⇒ if (!p.isCompleted) p.failure(cause) }
    }.toMat(Sink.ignore)(Keep.right)
  }
}
