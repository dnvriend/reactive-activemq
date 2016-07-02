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
package camel

import akka.actor.{ ActorLogging, Props }
import akka.camel.{ CamelMessage, Producer }
import akka.stream.actor.{ ActorSubscriber, OneByOneRequestStrategy, RequestStrategy }
import akka.stream.scaladsl.Sink

class CamelActorSubscriber(val endpointUri: String) extends Producer with ActorSubscriber with ActorLogging {
  override protected val requestStrategy: RequestStrategy = OneByOneRequestStrategy
}

object CamelFlow {
  def fromEndpointUri(endpointUri: String) =
    Sink.actorSubscriber[CamelMessage](Props(new CamelActorSubscriber(endpointUri)))
}
