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

import akka.camel.CamelMessage
import akka.stream.scaladsl.Sink
import com.github.dnvriend.activemq.TestSpec
import com.github.dnvriend.activemq.stream.AckedFlowOps._

import scala.concurrent.duration._

case class Foo()

class ActiveMqSourceTest extends TestSpec {
  implicit val FooExtractor = new MessageExtractor[CamelMessage, Foo] {
    override def extract(in: CamelMessage): Foo = Foo()
  }

  it should "consume messages from queue" in {
    ActiveMqSource("consumer1").runForeachAck(println).futureValue
  }
}
