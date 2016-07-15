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

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl._
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import akka.stream.testkit.{ TestPublisher, TestSubscriber }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import spray.json.DefaultJsonProtocol
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.stream.integration.activemq.{ ActiveMqConsumer, ActiveMqProducer }
import akka.stream.integration.xml.{ PersonParser, XMLEventSource }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import scala.xml.pull.XMLEvent
import JsonMessageExtractor._
import JsonMessageBuilder._
import akka.persistence.Persistence
import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal

object PersonDomain extends DefaultJsonProtocol {

  final case class Address(street: String = "", houseNumber: String = "", zipCode: String = "", city: String = "")

  final case class Person(firstName: String = "", lastName: String = "", age: Int = 0, address: Address = Address())

  implicit val jsonAddressFormat = jsonFormat4(Address)
  implicit val jsonPersonFormat = jsonFormat4(Person)
}

trait TestSpec extends FlatSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with OptionValues
    with Eventually
    with BrokerResources
    with InMemoryCleanup
    with ClasspathResources {

  import PersonDomain._

  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 60.seconds)
  implicit val timeout = Timeout(30.seconds)

  val writeJournal = Persistence(system).journalFor(null)

  val journal = PersistenceQuery(system)
    .readJournalFor(InMemoryReadJournal.Identifier)
    .asInstanceOf[ReadJournal with CurrentEventsByPersistenceIdQuery with EventsByTagQuery with CurrentEventsByTagQuery with EventsByPersistenceIdQuery]

  val testPerson1 = Person("Barack", "Obama", 54, Address("Pennsylvania Ave", "1600", "20500", "Washington"))
  val testPerson2 = Person("Anon", "Ymous", 42, Address("Here", "1337", "12345", "InUrBase"))

  final val PersonsXmlFile = "xml/persons.xml"
  final val LotOfPersonsXmlFile = "xml/lot-of-persons.xml"

  override def enableClearQueus: Boolean = true

  implicit class PimpedByteArray(self: Array[Byte]) {
    def getString: String = new String(self)
  }

  implicit class PimpedFuture[T](self: Future[T]) {
    def toTry: Try[T] = Try(self.futureValue)
  }

  def withTestTopicPublisher(endpoint: String = "PersonProducer")(f: TestPublisher.Probe[Person] ⇒ Unit): Unit = {
    f(TestSource.probe[Person].to(ActiveMqProducer[Person](endpoint)).run())
  }

  def withTestTopicSubscriber(endpoint: String = "PersonConsumer", within: FiniteDuration = 10.seconds)(f: TestSubscriber.Probe[AckUTup[Person]] ⇒ Unit): Unit = {
    val tp = ActiveMqConsumer[Person](endpoint).runWith(TestSink.probe[AckUTup[Person]])
    tp.within(within)(f(tp))
  }

  def withRequestResponseSubscriber(endpoint: String = "PersonConsumer")(f: TestSubscriber.Probe[AckTup[Person, Person]] ⇒ Unit): Unit = {
    f(ActiveMqConsumer[Person, Person](endpoint).runWith(TestSink.probe[AckTup[Person, Person]]))
  }

  def withTestXMLEventSource(within: FiniteDuration = 60.seconds)(filename: String)(f: TestSubscriber.Probe[XMLEvent] ⇒ Unit): Unit =
    withInputStream(filename) { is ⇒
      val tp = XMLEventSource.fromInputStream(is).runWith(TestSink.probe[XMLEvent])
      tp.within(within)(f(tp))
    }

  def withTestXMLPersonParser(within: FiniteDuration = 5.seconds)(filename: String)(f: TestSubscriber.Probe[Person] ⇒ Unit): Unit =
    withInputStream(filename) { is ⇒
      val tp = XMLEventSource.fromInputStream(is).via(PersonParser()).runWith(TestSink.probe[Person])
      tp.within(within)(f(tp))
    }

  implicit class SourceOps[A](src: Source[A, NotUsed]) {
    def testProbe(f: TestSubscriber.Probe[A] ⇒ Unit): Unit = {
      val tp = src.runWith(TestSink.probe(system))
      tp.within(10.seconds)(f(tp))
    }
  }

  Source(List(1, 2, 3)).runWith(Sink.seq)

  def randomId = UUID.randomUUID.toString

  def countJournal(pid: String): Future[Int] =
    journal.currentEventsByPersistenceId(pid, 0, Long.MaxValue).runWith(Sink.seq).map(_.size)

  override protected def afterAll(): Unit = {
    system.terminate().toTry should be a 'success
  }
}