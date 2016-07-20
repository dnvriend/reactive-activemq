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

import akka.actor.ActorSystem
import akka.camel.{ CamelExtension, CamelMessage }
import akka.stream.integration.activemq.extension.ActiveMqExtension
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.{ Done, NotUsed }
import org.apache.camel.ProducerTemplate

import scala.collection.JavaConversions._
import scala.concurrent.{ ExecutionContext, Future }

object ActiveMqProducer {

  private def send[A: CamelMessageBuilder](payload: A, producerName: String, endpointUri: String, producer: ProducerTemplate)(implicit ec: ExecutionContext): Future[A] = Future {
    val msg: CamelMessage = implicitly[CamelMessageBuilder[A]].build(payload)
    producer.sendBodyAndHeaders(endpointUri, msg.body, msg.headers.mapValues(_.asInstanceOf[AnyRef]))
    payload
  }

  /**
   * Creates a flow that produces messages to a configured ActiveMq producer until upstream terminates.
   */
  def flow[A: CamelMessageBuilder](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem): Flow[A, A, NotUsed] = {
    Flow[A].mapAsync(qos) { payload =>
      val producerTemplate = CamelExtension(system).template
      val endpointUri = ActiveMqExtension(system).producerEndpointUri(producerName)
      send(payload, producerName, endpointUri, producerTemplate)
    }
  }

  /**
   * Creates a sink that produces messages to a configured ActiveMq producer until upstream terminates.
   */
  def sink[A: CamelMessageBuilder](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem): Sink[A, Future[Done]] =
    flow(producerName, qos).toMat(Sink.ignore)(Keep.right)

  /**
   * Creates a sink that produces messages to a configured ActiveMq producer until upstream terminates.
   */
  def apply[A: CamelMessageBuilder](producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem): Sink[A, Future[Done]] =
    sink(producerName, qos)
}
