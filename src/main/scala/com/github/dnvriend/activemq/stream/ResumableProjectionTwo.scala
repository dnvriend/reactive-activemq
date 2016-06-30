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

import akka.persistence.PersistentActor
import akka.persistence.query.EventEnvelope
import akka.stream.actor.{ ActorPublisher, ActorSubscriber, RequestStrategy, ZeroRequestStrategy }

/**
 * Is a publisher for another stream,
 * Is a subscriber for the underlying query
 */
class ResumableProjectionTwo(projectionName: String) extends PersistentActor with ActorPublisher[EventEnvelope] with ActorSubscriber {
  override protected def requestStrategy: RequestStrategy = ZeroRequestStrategy
  override val persistenceId: String = projectionName

  override def receiveRecover: Receive = ???

  override def receiveCommand: Receive = ???

}
