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

import scala.concurrent.Promise

class ActiveMqProducerSourceTest extends TestSpec {
  it should "consume messages from the queue" in {
    withTestTopicSubscriber() { sub =>
      withTestTopicPublisher() { pub =>
        pub.sendNext(testPerson1)
        pub.sendComplete()

        sub.request(1)
        sub.expectNextPF {
          case (p: Promise[Unit], `testPerson1`) => p.success(())
        }
        sub.cancel()
      }
    }
  }
}
