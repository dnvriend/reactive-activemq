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
package camel

import akka.actor.{ ActorLogging, ActorRef, Props }
import akka.camel.{ CamelMessage, Consumer }
import akka.event.LoggingReceive
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.scaladsl.Source

class CamelActorPublisher(val endpointUri: String) extends Consumer with ActorPublisher[(ActorRef, CamelMessage)] with ActorLogging {
  override val autoAck: Boolean = false

  override def receive: Receive = LoggingReceive {
    case CamelMessage if totalDemand == 0 =>
      sender() ! akka.actor.Status.Failure(new IllegalStateException("No demand for new messages"))

    case msg: CamelMessage => onNext((sender(), msg))

    case Cancel            => context stop self
  }
}

class CamelActorPublisherWithExtractor[A: CamelMessageExtractor](val endpointUri: String) extends Consumer with ActorPublisher[(ActorRef, A)] with ActorLogging {
  override val autoAck: Boolean = false

  override def receive: Receive = LoggingReceive {
    case CamelMessage if totalDemand == 0 =>
      sender() ! akka.actor.Status.Failure(new IllegalStateException("No demand for new messages"))

    case msg: CamelMessage =>
      try {
        onNext((sender(), implicitly[CamelMessageExtractor[A]].extract(msg)))
      } catch {
        case t: Throwable =>
          log.error(t, "Removing message from the broker because of error while extracting the message")
          sender() ! akka.camel.Ack
      }

    case Cancel => context stop self
  }
}

object CamelActorPublisher {
  def fromEndpointUri(endpointUri: String): Source[AckRefTup[CamelMessage], ActorRef] =
    Source.actorPublisher[AckRefTup[CamelMessage]](Props(new CamelActorPublisher(endpointUri)))

  def fromEndpointUriWithExtractor[A: CamelMessageExtractor](endpointUri: String): Source[AckRefTup[A], ActorRef] =
    Source.actorPublisher[AckRefTup[A]](Props(new CamelActorPublisherWithExtractor(endpointUri)))
}
