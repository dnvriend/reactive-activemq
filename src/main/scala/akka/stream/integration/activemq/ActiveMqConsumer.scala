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

import akka.actor.{ ActorRef, ActorSystem }
import akka.camel.CamelMessage
import akka.stream.integration.activemq.extension.ActiveMqExtension
import akka.stream.integration.camel.CamelActorPublisher
import akka.stream.scaladsl.Source

import scala.concurrent.ExecutionContext

object ActiveMqConsumer {
  /**
   * Creates a consumer that consumes messages from a configured ActiveMq consumer until upstream terminates.
   * The consumed messages must be consumed by an [[akka.stream.integration.activemq.AckSink]] or [[akka.persistence.stream.AckJournal]]
   * for before the source will emit the next element.
   */
  def apply[A](consumerName: String)(implicit ec: ExecutionContext, system: ActorSystem, extractor: MessageExtractor[CamelMessage, A]): Source[AckUTup[A], ActorRef] =
    source(consumerName)

  /**
   * Creates a consumer that consumes messages from a configured ActiveMq consumer and produces responses to the
   * supplied response destination until upstream terminates. A consumed messages must be acknowledged by an
   * [[akka.stream.integration.activemq.AckSink]] completion Sink before the source will emit the next element.
   */
  def apply[A, B](consumerName: String)(implicit ec: ExecutionContext, system: ActorSystem, extractor: MessageExtractor[CamelMessage, B], builder: MessageBuilder[A, CamelMessage]): Source[AckTup[A, B], ActorRef] =
    requestResponseSource(consumerName)

  /**
   * Creates a consumer that consumes messages from a configured ActiveMq consumer until upstream terminates.
   * The consumed messages must be consumed by an [[akka.stream.integration.activemq.AckSink]] or [[akka.persistence.stream.AckJournal]]
   * for before the source will emit the next element.
   */
  def source[A](consumerName: String)(implicit ec: ExecutionContext, system: ActorSystem, extractor: MessageExtractor[CamelMessage, A]): Source[AckUTup[A], ActorRef] =
    CamelActorPublisher.fromEndpointUriWithExtractor[A](ActiveMqExtension(system).consumerEndpointUri(consumerName)).via(new AckedFlow)

  /**
   * Creates a consumer that consumes messages from a configured ActiveMq consumer and produces responses to the
   * supplied response destination until upstream terminates. A consumed messages must be acknowledged by an
   * [[akka.stream.integration.activemq.AckSink]] completion Sink before the source will emit the next element.
   */
  def requestResponseSource[A, B](consumerName: String)(implicit ec: ExecutionContext, system: ActorSystem, extractor: MessageExtractor[CamelMessage, B], builder: MessageBuilder[A, CamelMessage]): Source[AckTup[A, B], ActorRef] =
    CamelActorPublisher.fromEndpointUriWithExtractor[B](ActiveMqExtension(system).consumerEndpointUri(consumerName)).via(new AckedResponseFlow)
}
