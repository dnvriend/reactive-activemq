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

package com.github.dnvriend.activemq

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import akka.stream.testkit.{ TestPublisher, TestSubscriber }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import com.github.dnvriend.activemq.BrokerResources.QueueStat
import com.github.dnvriend.activemq.stream.JsonMessageBuilder._
import com.github.dnvriend.activemq.stream.JsonMessageExtractor._
import com.github.dnvriend.activemq.stream._
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object PersonDomain extends DefaultJsonProtocol {
  final case class Address(street: String, houseNumber: String, zipCode: String)
  final case class Person(firstName: String, lastName: String, age: Int, address: Address)

  implicit val jsonAddressFormat = jsonFormat3(Address)
  implicit val jsonPersonFormat = jsonFormat4(Person)
}

trait TestSpec extends FlatSpec with Matchers with ScalaFutures with BrokerResources with BeforeAndAfterEach with BeforeAndAfterAll with OptionValues with Eventually {
  import PersonDomain._
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 60.seconds)
  implicit val timeout = Timeout(30.seconds)

  val testPerson = Person("Barack", "Obama", 54, Address("Pennsylvania Ave", "1600", "20500"))

  implicit class PimpedByteArray(self: Array[Byte]) {
    def getString: String = new String(self)
  }

  implicit class PimpedFuture[T](self: Future[T]) {
    def toTry: Try[T] = Try(self.futureValue)
  }

  def queueStatFor(topic: String): Option[QueueStat] =
    getQueueStats.find(_.name contains topic)

  def eventuallyMessageIsConsumed(topic: String): Unit = eventually {
    val stats = queueStatFor(topic)
    stats.value.enqueueCount equals stats.value.dequeueCount
  }

  def sendMessageEventuallyConsumedJson(json: String, topic: String): Unit = {
    println(s"Sending to $topic':\n$json")
    //    sendMessageJson(json, topic)
    eventuallyMessageIsConsumed(topic)
  }

  def withTestTopicPublisher()(f: TestPublisher.Probe[Person] ⇒ Unit): Unit =
    f(TestSource.probe[Person].to(ActiveMqSink[Person]("PersonProducer")).run())

  def withTestTopicSubscriber()(f: TestSubscriber.Probe[AckTup[Person]] ⇒ Unit): Unit =
    f(ActiveMqSource[Person]("PersonConsumer").runWith(TestSink.probe[AckTup[Person]](system)))

  def randomId = UUID.randomUUID.toString

  override protected def beforeEach(): Unit = {
    purgeQueues()
  }
}