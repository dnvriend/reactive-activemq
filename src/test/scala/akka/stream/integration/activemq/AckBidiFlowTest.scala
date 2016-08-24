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

import akka.stream.integration.PersonDomain.Person

import scala.concurrent.Promise
import scala.concurrent.duration._

class AckBidiFlowTest extends ActiveMqTestSpec {

  behavior of "AckBidiFlow"

  it should "propagate an element downstream, and propagate returned elements upstream, wrapped with the initial promise" in {
    withBackendFlow[Person, Person] { implicit backendFlow => flowProbe =>
      withAckBidiFlow { inputProbe => outputProbe =>

        val inputPromise = Promise[Unit]()

        inputProbe.sendNext((inputPromise, testPerson1))

        flowProbe.expectMsg(testPerson1)

        outputProbe.request(1)

        outputProbe.expectNoMsg(100.milliseconds)

        flowProbe.reply(testPerson1)

        val outputPromise = outputProbe.expectNextPF { case (p: Promise[_], `testPerson1`) => p }

        inputPromise should equal(outputPromise)
      }
    }
  }

  it should "zip incoming promises with back-end values" in {
    withBackendFlow[Person, Person] { implicit backendFlow => flowProbe =>
      withAckBidiFlow { inputProbe => outputProbe =>

        val inputPromise1 = Promise[Unit]()
        val inputPromise2 = Promise[Unit]()

        inputProbe.sendNext((inputPromise1, testPerson1))
        inputProbe.sendNext((inputPromise2, testPerson2))
        inputProbe.sendComplete()

        flowProbe.expectMsg(testPerson1)
        flowProbe.reply(testPerson2)

        outputProbe.request(2)
        outputProbe.expectNextPF { case (`inputPromise1`, `testPerson2`) => }

        flowProbe.expectMsg(testPerson2)
        flowProbe.reply(testPerson1)

        outputProbe.expectNextPF { case (`inputPromise2`, `testPerson1`) => }
        outputProbe.expectComplete()

        outputProbe.cancel()
      }
    }
  }

  it should "respect buffer size" in {
    withBackendFlow[Person, Person] { implicit backendFlow => flowProbe =>
      withAckBidiFlow { inputProbe => outputProbe =>

        val inputPromise1 = Promise[Unit]()
        val inputPromise2 = Promise[Unit]()
        inputProbe.sendNext((inputPromise1, testPerson1))
        inputProbe.sendNext((inputPromise2, testPerson2))

        outputProbe.request(2)

        flowProbe.expectMsg(testPerson1)

        // assert that buffer-size of 1 is respected
        flowProbe.expectNoMsg(100.milliseconds)
      }
    }
  }
}
