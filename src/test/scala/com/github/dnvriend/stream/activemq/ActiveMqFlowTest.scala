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

import akka.stream.scaladsl.Flow
import com.github.dnvriend.stream.PersonDomain.Person

import scala.concurrent.Promise

/**
 *
 */
class ActiveMqFlowTest extends ActiveMqTestSpec {

  behavior of "ActiveMqFlow"

  it should "propagate messages from input to output unmodified, if mediated by the identity flow" in {
    withTestTopicPublisher("AckBidiFlowTestInput") { pub ⇒
      withTestTopicSubscriber("AckBidiFlowTestOutput") { sub ⇒
        withActiveMqBidiFlow("AckBidiFlowTestInput", "AckBidiFlowTestOutput") { flow ⇒

          val identityFlow = Flow[Person].map(identity)
          flow.join(identityFlow).run()

          pub.sendNext(testPerson1)

          sub.request(2)
          sub.expectNextPF {
            case (p: Promise[Unit], `testPerson1`) ⇒ p.success(())
          }

          pub.sendNext(testPerson2)

          sub.expectNextPF {
            case (p: Promise[Unit], `testPerson2`) ⇒ p.success(())
          }

          pub.sendComplete()
          sub.cancel()
        }
      }
    }
  }
}
