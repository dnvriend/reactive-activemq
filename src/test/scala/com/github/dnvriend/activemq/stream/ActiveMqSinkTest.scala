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

import akka.stream.scaladsl.Source
import com.github.dnvriend.activemq.{ PersonDomain, TestSpec }

import scala.concurrent.Promise
import scala.concurrent.duration._

class ActiveMqSinkTest extends TestSpec {
  it should "produce messages to a queue" in {
    withTestTopicSubscriber() { sub ⇒
      withTestTopicPublisher() { pub ⇒
        pub.sendNext(testPerson)
        pub.sendComplete()

        sub.request(1)
        sub.expectNextPF {
          case (p: Promise[Unit], `testPerson`) ⇒ p.success(())
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
          pub.sendNext(testPerson)
          sub.request(1)
          sub.expectNextPF {
            case (p: Promise[Unit], `testPerson`) ⇒ p.success(())
          }
        }

        pub.sendComplete()
        sub.cancel()
      }
    }
  }

  it should "send 250 messages to the queue" in {
    import PersonDomain._
    import JsonMessageBuilder._
    import JsonMessageExtractor._
    val numberOfPersons = 250
    Source.repeat(testPerson).take(numberOfPersons).runWith(ActiveMqSink("PersonProducer")).toTry should be a 'success
    val xs = ActiveMqSource[Person]("PersonConsumer").take(numberOfPersons).runWith(AckSink.seq).futureValue
    xs should not be 'empty
    xs.size shouldBe numberOfPersons
    xs.foreach { _ shouldBe `testPerson` }
  }
}
