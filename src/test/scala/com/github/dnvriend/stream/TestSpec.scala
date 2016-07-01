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

package com.github.dnvriend.stream

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.{ CurrentEventsByPersistenceIdQuery, EventsByPersistenceIdQuery, EventsByTagQuery, ReadJournal }
import akka.stream.scaladsl.Sink
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import akka.stream.testkit.{ TestPublisher, TestSubscriber }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import com.github.dnvriend.stream.BrokerResources.QueueStat
import com.github.dnvriend.stream.activemq._
import com.github.dnvriend.stream.camel.JsonMessageBuilder._
import com.github.dnvriend.stream.camel.JsonMessageExtractor._
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

trait TestSpec extends FlatSpec with Matchers with ScalaFutures with BrokerResources with BeforeAndAfterEach with BeforeAndAfterAll with OptionValues with Eventually with DatabaseResources {
  import PersonDomain._
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 60.seconds)
  implicit val timeout = Timeout(30.seconds)

  val journal = PersistenceQuery(system)
    .readJournalFor("jdbc-read-journal")
    .asInstanceOf[ReadJournal with CurrentEventsByPersistenceIdQuery with EventsByTagQuery with EventsByPersistenceIdQuery]

  val testPerson = Person("Barack", "Obama", 54, Address("Pennsylvania Ave", "1600", "20500"))

  implicit class PimpedByteArray(self: Array[Byte]) {
    def getString: String = new String(self)
  }

  implicit class PimpedFuture[T](self: Future[T]) {
    def toTry: Try[T] = Try(self.futureValue)
  }

  def withTestTopicPublisher()(f: TestPublisher.Probe[Person] ⇒ Unit): Unit =
    f(TestSource.probe[Person].to(ActiveMqSink[Person]("PersonProducer")).run())

  def withTestTopicSubscriber()(f: TestSubscriber.Probe[AckTup[Person]] ⇒ Unit): Unit =
    f(ActiveMqSource[Person]("PersonConsumer").runWith(TestSink.probe[AckTup[Person]](system)))

  def randomId = UUID.randomUUID.toString

  def countJournal(pid: String): Future[Int] =
    journal.currentEventsByPersistenceId(pid, 0, Long.MaxValue).runWith(Sink.seq).map(_.size)

  override protected def beforeEach(): Unit = {
    purgeQueues()
    clearTables()
  }

  override protected def afterAll(): Unit = {
    system.terminate().toTry should be a 'success
  }
}