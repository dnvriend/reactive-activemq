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

package com.github.dnvriend.stream.activemq

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.scaladsl.{ Flow, Keep }
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import akka.stream.testkit.{ TestPublisher, TestSubscriber }
import akka.testkit.TestActor.AutoPilot
import akka.testkit.TestProbe
import com.github.dnvriend.stream.JsonMessageBuilder._
import com.github.dnvriend.stream.JsonMessageExtractor._
import com.github.dnvriend.stream.PersonDomain.Person
import com.github.dnvriend.stream._

import scala.util.{ Failure, Success, Try }
/**
 * Test fixtures for ActiveMq
 */
trait ActiveMqTestSpec extends TestSpec {

  /**
   * Creates an acknowledging bidirectional flow whose back-end and output can be manipulated using probes; it expects
   * an implicit backendFlow such as can be acquired from withBackendFlow
   */
  def withAckBidiFlow(test: TestPublisher.Probe[AckUTup[Person]] ⇒ TestSubscriber.Probe[AckUTup[Person]] ⇒ Any)(implicit backendFlow: Flow[Person, Person, NotUsed]): Unit = {

    val inputSource = TestSource.probe[AckUTup[Person]]
    val outputSink = TestSink.probe[AckUTup[Person]]

    val partialTestFlow = ActiveMqFlow.applyMat(inputSource, outputSink)(Keep.both)

    val (inputProbe, outputProbe) = partialTestFlow.join(backendFlow).run()

    test(inputProbe)(outputProbe)
  }

  /**
   * Creates a back-end flow whose messages can be intercepted using a traditional testprobe
   */
  def withBackendFlow(test: Flow[Person, Person, NotUsed] ⇒ TestProbe ⇒ Any): Unit = {
    import akka.pattern.ask
    val flowProbe = TestProbe()
    val backendFlow = Flow[Person].mapAsync(1)(p ⇒ (flowProbe.ref ? p).mapTo[Person])
    test(backendFlow)(flowProbe)
  }

  /**
   * Creates an acknowledging bidirectional flow, connected with an ActiveMqSource and ActiveMqSink
   *
   * NOTE: Test using this fixture assume correct implementation of ActiveMqSource and ActiveMqSink!
   */
  def withActiveMqBidiFlow[S, T](sourceEndpoint: String, sinkEndpoint: String)(test: Flow[Person, Person, NotUsed] ⇒ Any): Unit =
    test(ActiveMqFlow[Person, Person](sourceEndpoint, sinkEndpoint))

  /**
   * Creates a request-response flow with an ActiveMqSource, acknowledging sink and bidi-flow to join them.
   */
  def withReqRespBidiFlow[S, T](sourceEndpoint: String)(test: Flow[Person, Person, NotUsed] ⇒ Any): Any = {
    test(ActiveMqReqRespFlow[Person, Person](sourceEndpoint))
  }

  /**
   * Creates an AutoPilot that performs request-response according to the supplied function
   */
  implicit def function1ToAutoPilot[S, T](f: S ⇒ T): AutoPilot = new AutoPilot {
    override def run(sender: ActorRef, msg: Any): AutoPilot = msg match {
      case s: S ⇒
        val tryT: Try[T] = Try(f(s))
        tryT match {
          case Success(t) ⇒
            sender ! t
            function1ToAutoPilot(f)
          case Failure(f) ⇒
            fail(s"Failed to apply supplied function to received message: $s", f)
        }
      case _ ⇒
        fail(s"Received message is not of the required type: $msg")
    }
  }
}
