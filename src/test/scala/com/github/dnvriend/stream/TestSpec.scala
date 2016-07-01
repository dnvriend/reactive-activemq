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
import akka.event.{Logging, LoggingAdapter}
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.{CurrentEventsByPersistenceIdQuery, EventsByPersistenceIdQuery, EventsByTagQuery, ReadJournal}
import akka.stream.scaladsl.Sink
import akka.stream.testkit.TestPublisher.Probe
import akka.stream.testkit.TestSubscriber.Probe
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.github.dnvriend.stream.activemq._
import com.github.dnvriend.stream.camel.JsonMessageBuilder._
import com.github.dnvriend.stream.camel.JsonMessageExtractor._
import com.github.dnvriend.stream.xml.{ PersonParser, XMLEventSource }
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.xml.pull.XMLEvent

object PersonDomain extends DefaultJsonProtocol {
  final case class Address(street: String = "", houseNumber: String = "", zipCode: String = "", city: String = "")
  final case class Person(firstName: String = "", lastName: String = "", age: Int = 0, address: Address = Address())

  implicit val jsonAddressFormat = jsonFormat4(Address)
  implicit val jsonPersonFormat = jsonFormat4(Person)
}

trait TestSpec extends FlatSpec
    with Matchers
    with ScalaFutures
    with BrokerResources
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with OptionValues
    with Eventually
    with DatabaseResources
    with ClasspathResources {

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

  val testPerson1 = Person("Barack", "Obama", 54, Address("Pennsylvania Ave", "1600", "20500", "Washington"))
  val testPerson2 = Person("Anon", "Ymous", 42, Address("Here", "1337", "12345", "InUrBase"))

  final val PersonsXmlFile = "xml/persons.xml"
  final val LotOfPersonsXmlFile = "xml/lot-of-persons.xml"

  implicit class PimpedByteArray(self: Array[Byte]) {
    def getString: String = new String(self)
  }

  implicit class PimpedFuture[T](self: Future[T]) {
    def toTry: Try[T] = Try(self.futureValue)
  }

  def withTestTopicPublisher(endpoint: String = "PersonProducer")(f: TestPublisher.Probe[Person] ⇒ Unit): Unit =
    f(TestSource.probe[Person].to(ActiveMqSink[Person](endpoint)).run())

  def withTestTopicSubscriber(endpoint: String = "PersonConsumer")(f: TestSubscriber.Probe[AckTup[Person]] ⇒ Unit): Unit =
    f(ActiveMqSource[Person](endpoint).runWith(TestSink.probe[AckTup[Person]](system)))

  def withTestXMLEventSource(within: FiniteDuration = 60.seconds)(filename: String)(f: TestSubscriber.Probe[XMLEvent] ⇒ Unit): Unit =
    withInputStream(filename) { is ⇒
      val tp = XMLEventSource.fromInputStream(is).runWith(TestSink.probe[XMLEvent])
      tp.within(within)(f(tp))
    }

  def withTestXMLPersonParser(within: FiniteDuration = 60.seconds)(filename: String)(f: TestSubscriber.Probe[Person] ⇒ Unit): Unit =
    withInputStream(filename) { is ⇒
      val tp = XMLEventSource.fromInputStream(is).via(PersonParser()).runWith(TestSink.probe[Person])
      tp.within(60.seconds)(f(tp))
    }

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