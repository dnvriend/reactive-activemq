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

import scala.concurrent.Promise
import scala.concurrent.duration._

class ActiveMqProducerSourceTest extends TestSpec {

  it should "consume messages from the queue" in {
    withTestTopicSubscriber() { sub =>
      withTestTopicPublisher() { pub =>
        pub.sendNext(testPerson1)
        pub.sendComplete()

        sub.request(1)
        sub.expectNextPF {
          case (p: Promise[Unit]@unchecked, `testPerson1`) => p.success(())
        }
        sub.cancel()
      }
    }
  }

  it should "support concurrent subscribers" in {
    withTestTopicSubscriber(poolSize = 1) { sub1 =>
      withTestTopicSubscriber(poolSize = 1) { sub2 =>

        withTestTopicPublisher() { pub =>
          val testPersons = (1 to 2).map(i => testPerson2.copy(age = i))
          testPersons foreach pub.sendNext
          pub.sendComplete()

          sub1.request(1)
          sub2.request(1)

          // make sure both subscribers receive the one message requested
          val (prom1, per1) = sub1.expectNextPF { case (p: Promise[Unit]@unchecked, person) if testPersons.contains(person) => (p, person) }
          val (prom2, per2) = sub2.expectNextPF { case (p: Promise[Unit]@unchecked, person) if testPersons.contains(person) => (p, person) }

          List(prom1, prom2) foreach (_.success(()))

          // two messages were produced, two are consumed, nothing should remain
          sub1.request(1)
          sub2.request(1)
          sub1.expectNoMsg(100.millis)
          sub2.expectNoMsg(100.millis)

          // make sure we have no duplicates
          per1 should not be per2

          sub1.cancel()
          sub2.cancel()
        }
      }
    }
  }

  it should "support a subscriber pool which can handle requests concurrently" in {
    eventually(consumerCount shouldBe 0)

    withTestTopicSubscriber(poolSize = 3) { sub =>

      withTestTopicPublisher() { pub =>
        val testPersons = (1 to 5).map(i => testPerson2.copy(age = i))

        // Make sure all consumers are up before sending messages (otherwise all messages on queue are allocated to a single consumer)
        eventually(consumerCount shouldBe 3)
        testPersons foreach pub.sendNext
        pub.sendComplete()

        sub.request(5)
        val (prom1, per1) = sub.expectNextPF { case (p: Promise[Unit]@unchecked, person) if testPersons.contains(person) => (p, person) }
        val (prom2, per2) = sub.expectNextPF { case (p: Promise[Unit]@unchecked, person) if testPersons.contains(person) => (p, person) }
        val (prom3, per3) = sub.expectNextPF { case (p: Promise[Unit]@unchecked, person) if testPersons.contains(person) => (p, person) }
        sub.expectNoMsg(100.millis)

        List(prom1, prom2, prom3) foreach (_.success(()))

        val (prom4, per4) = sub.expectNextPF { case (p: Promise[Unit]@unchecked, person) if testPersons.contains(person) => (p, person) }
        val (prom5, per5) = sub.expectNextPF { case (p: Promise[Unit]@unchecked, person) if testPersons.contains(person) => (p, person) }

        List(prom4, prom5) foreach (_.success(()))

        // make sure we have no duplicates
        val firstBatch = List(per1, per2, per3, per4, per5)
        firstBatch.distinct should contain theSameElementsAs firstBatch

        sub.cancel()
      }
    }
  }

  def consumerCount: Int = getQueueStatFor("PersonConsumer").map(_.consumerCount).getOrElse(0)
}
