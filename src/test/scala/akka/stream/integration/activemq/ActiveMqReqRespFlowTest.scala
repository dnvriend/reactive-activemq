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

import akka.actor.ActorRef
import akka.stream.integration.PersonDomain.Person

import scala.concurrent.Promise

/**
 *
 */
class ActiveMqReqRespFlowTest extends ActiveMqTestSpec {

  behavior of "ActiveMqReqRespFlow"

  it should "support request-response for a single message" in {
    withBackendFlow { implicit backendFlow => flowProbe =>
      withReqRespBidiFlow("AckBidiFlowReqRespTestInput") { testFlow =>
        var ref: ActorRef = null
        withTestTopicPublisher("AckBidiFlowReqRespTestInput") { pub =>
          withTestTopicSubscriber("AckBidiFlowReqRespTestOutput") { sub =>

            // echo all received messages
            flowProbe.setAutoPilot(identity[Person] _)
            ref = testFlow.join(backendFlow).run()

            sub.request(2)
            pub.sendNext(testPerson1)

            sub.expectNextPF {
              case (p: Promise[Unit], `testPerson1`) => p.success(())
            }

            sub.cancel()
            pub.sendComplete()
          }
        }
        ref
      }
    }
  }

  it should "support request-response for a multiple messages" in {
    withBackendFlow { implicit backendFlow => flowProbe =>
      withReqRespBidiFlow("AckBidiFlowReqRespTestInput") { testFlow =>
        var ref: ActorRef = null
        withTestTopicPublisher("AckBidiFlowReqRespTestInput") { pub =>
          withTestTopicSubscriber("AckBidiFlowReqRespTestOutput") { sub =>

            // echo all received messages
            flowProbe.setAutoPilot(identity[Person] _)
            ref = testFlow.join(backendFlow).run()

            sub.request(2)

            pub.sendNext(testPerson1)
            sub.expectNextPF {
              case (p: Promise[Unit], `testPerson1`) => p.success(())
            }

            pub.sendNext(testPerson2)
            sub.expectNextPF {
              case (p: Promise[Unit], `testPerson2`) => p.success(())
            }

            sub.cancel()
            pub.sendComplete()
          }
        }
        ref
      }
    }
  }
}
