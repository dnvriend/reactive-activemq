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

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.integration.activemq.extension.ActiveMqExtension
import akka.stream.integration.camel.CamelActorPublisher
import akka.stream.scaladsl.{Merge, Source}

import scala.concurrent.ExecutionContext

object ActiveMqConsumer {
  /**
   * Creates a consumer that consumes messages from a configured ActiveMq consumer until upstream terminates.
   * The consumed messages must be consumed by an [[akka.stream.integration.activemq.AckSink]]
   * for before the source will emit the next element.
   */
  def apply[A: CamelMessageExtractor](consumerName: String, poolSize: Int = 1)(implicit ec: ExecutionContext, system: ActorSystem): Source[AckUTup[A], NotUsed] =
    source(consumerName, poolSize)

  /**
   * Creates a consumer that consumes messages from a configured ActiveMq consumer and produces responses to the
   * supplied response destination until upstream terminates. A consumed messages must be acknowledged by an
   * [[akka.stream.integration.activemq.AckSink]] completion Sink before the source will emit the next element.
   */
  def apply[A: CamelMessageBuilder, B: CamelMessageExtractor](consumerName: String, poolSize: Int)(implicit ec: ExecutionContext, system: ActorSystem): Source[AckTup[A, B], NotUsed] =
    requestResponseSource(consumerName, poolSize)

  /**
   * Creates a consumer that consumes messages from a configured ActiveMq consumer until upstream terminates.
   * The consumed messages must be consumed by an [[akka.stream.integration.activemq.AckSink]]
   * for before the source will emit the next element.
   */
  def source[A: CamelMessageExtractor](consumerName: String, poolSize: Int = 1)
                                      (implicit ec: ExecutionContext, system: ActorSystem): Source[AckUTup[A], NotUsed] =
    createActorPublishers(consumerName, system, poolSize).via(new AckedFlow)


  /**
   * Creates a consumer that consumes messages from a configured ActiveMq consumer and produces responses to the
   * supplied response destination until upstream terminates. A consumed messages must be acknowledged by an
   * [[akka.stream.integration.activemq.AckSink]] completion Sink before the source will emit the next element.
   */
  def requestResponseSource[A: CamelMessageBuilder, B: CamelMessageExtractor](consumerName: String, poolSize: Int = 1)
                                                                             (implicit ec: ExecutionContext, system: ActorSystem): Source[AckTup[A, B], NotUsed] =
  createActorPublishers(consumerName, system, poolSize).via(new AckedResponseFlow)


  /**
    * Instantiates a single camel-actor-publisher for the given configuration
    */
  protected def createActorPublisher[A: CamelMessageExtractor](consumerName: String, system: ActorSystem): Source[(ActorRef, A), ActorRef] =
    CamelActorPublisher.fromEndpointUriWithExtractor[A](ActiveMqExtension(system).consumerEndpointUri(consumerName))


  /**
    * Creates a pool of camel-actor-publishers of size at least one. This defines the number of requests that can
    * be in-transit in parallel.
    */
  protected def createActorPublishers[A: CamelMessageExtractor](consumerName: String, system: ActorSystem, poolSize: Int = 1): Source[(ActorRef, A), NotUsed] = {
    poolSize match {
      case 1 =>
        createActorPublisher(consumerName, system).mapMaterializedValue(_ => NotUsed)

      case n if n > 1 =>
        val consumer1 = createActorPublisher(consumerName, system)
        val consumer2 = createActorPublisher(consumerName, system)
        val rest = (1 to n - 2).map(_ => createActorPublisher(consumerName, system))
        Source.combine(consumer1, consumer2, rest: _*)(Merge(_))

      case n =>
        throw new java.lang.AssertionError(s"Expected positive value for poolSize, but got: $n")
    }
  }
}
