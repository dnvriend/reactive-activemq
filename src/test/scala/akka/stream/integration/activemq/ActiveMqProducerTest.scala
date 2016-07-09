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
import akka.stream.scaladsl.Source

import scala.concurrent.Promise
import scala.concurrent.duration._
import JsonMessageBuilder._
import JsonMessageExtractor._

class ActiveMqProducerTest extends TestSpec {
  it should "produce messages to a queue" in {
    withTestTopicSubscriber() { sub ⇒
      withTestTopicPublisher() { pub ⇒
        pub.sendNext(testPerson1)
        pub.sendComplete()

        sub.request(1)
        sub.expectNextPF {
          case (p: Promise[Unit], `testPerson1`) ⇒ p.success(())
        }

        sub.expectNoMsg(500.millis)
        sub.cancel()
      }
    }
  }

  it should "produce multiple messages to a queue" in {
    withTestTopicSubscriber() { sub ⇒
      withTestTopicPublisher() { pub ⇒

        (0 to 10).foreach { _ ⇒
          pub.sendNext(testPerson1)
          sub.request(1)
          sub.expectNextPF {
            case (p: Promise[Unit], `testPerson1`) ⇒ p.success(())
          }
        }
        pub.sendComplete()
        sub.cancel()
      }
    }
  }

  it should "send 250 messages to the queue" in {
    import JsonMessageBuilder._
    import PersonDomain._
    val numberOfPersons = 250
    Source.repeat(testPerson1).take(numberOfPersons).runWith(ActiveMqProducer("PersonProducer")).toTry should be a 'success
  }

  it should "send and receive 250 messages from the queue" in {
    val numberOfPersons = 250
    Source.repeat(testPerson1).take(numberOfPersons).runWith(ActiveMqProducer[Person]("PersonProducer")).toTry should be a 'success
    ActiveMqConsumer[Person]("PersonConsumer").take(numberOfPersons).runWith(AckSink.seq).toTry should be a 'success
  }

  //  it should "copy messages from queue and put on topic" in {
  //    Source.repeat(testPerson1).take(10).runWith(ActiveMqProducer[Person]("PersonProducer"))
  //    eventually(getQueueMessageCount("Consumer.PersonConsumer.VirtualTopic.Person").value shouldBe 10)
  //    ActiveMqConsumer[Person]("PersonConsumer").take(10).runWith(AckActiveMqProducer[Person]("PersonCopyProducer")).futureValue
  //        eventually(getQueueMessageCount("Consumer.PersonConsumer.VirtualTopic.PersonCopy").value shouldBe 10)
  //  }
}
