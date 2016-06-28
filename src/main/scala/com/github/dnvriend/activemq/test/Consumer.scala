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

package com.github.dnvriend.activemq.test

import akka.actor.ActorSystem
import akka.camel.CamelMessage
import akka.stream.{ ActorMaterializer, Materializer }
import com.github.dnvriend.activemq.stream.{ ActiveMqSource, MessageExtractor }
import com.github.dnvriend.activemq.stream.AckedFlowOps._

import scala.concurrent.ExecutionContext

case class Foo(txt: String)

object Consumer extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  sys.addShutdownHook(system.terminate())

  implicit val FooExtractor = new MessageExtractor[CamelMessage, Foo] {
    override def extract(in: CamelMessage): Foo = {
      //      throw new RuntimeException("YOLO!!")
      Foo(in.body.asInstanceOf[String])
    }
  }
  println("Launching consumers")
  ActiveMqSource("consumer1").fmap(foo ⇒ foo.copy(txt = foo.txt + "c1!")).runForeachAck(txt ⇒ println(txt + "c1"))
  ActiveMqSource("consumer2").fmap(foo ⇒ foo.copy(txt = foo.txt + "c2!")).runForeachAck(txt ⇒ println(txt + "c2"))
}
