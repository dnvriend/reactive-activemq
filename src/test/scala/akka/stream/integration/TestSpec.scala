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

import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import akka.camel.CamelExtension
import akka.event.{ Logging, LoggingAdapter }
import akka.stream.integration.activemq.{ ActiveMqConsumer, ActiveMqProducer }
import akka.stream.scaladsl.{ Keep, Source }
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import akka.stream.testkit.{ TestPublisher, TestSubscriber }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.testkit.TestProbe
import akka.util.Timeout
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object PersonDomain extends DefaultJsonProtocol {
  final case class Address(street: String = "", houseNumber: String = "", zipCode: String = "", city: String = "")
  final case class Person(firstName: String = "", lastName: String = "", age: Int = 0, address: Address = Address())

  implicit val jsonAddressFormat = jsonFormat4(Address)
  implicit val jsonPersonFormat = jsonFormat4(Person)

  implicit val noHeadersBuilder = NoHeadersBuilder.noHeadersBuilder[Person]
  implicit val personCamelMessageBuilder = JsonCamelMessageBuilder.jsonMessageBuilder[Person]
  implicit val personCamelMessageExtractor = JsonCamelMessageExtractor.jsonMessageExtractor[Person]
}

trait TestSpec extends FlatSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with OptionValues
    with Eventually
    with BrokerResources
    with ClasspathResources {

  import PersonDomain._

  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  val log: LoggingAdapter = Logging.getLogger(system, this.getClass)
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 60.seconds)
  implicit val timeout = Timeout(30.seconds)

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

  def withTestTopicPublisher(endpoint: String = "PersonProducer")(f: TestPublisher.Probe[Person] => Unit): Unit =
    f(TestSource.probe[Person].to(ActiveMqProducer[Person](endpoint)).run())

  def withTestTopicSubscriber(endpoint: String = "PersonConsumer")(f: TestSubscriber.Probe[AckUTup[Person]] => Unit): Unit =
    ActiveMqConsumer[Person](endpoint).testProbe(f)

  def withRequestResponseSubscriber(endpoint: String = "PersonConsumer")(f: TestSubscriber.Probe[AckTup[Person, Person]] => Unit): Unit =
    ActiveMqConsumer[Person, Person](endpoint).testProbe(f)

  def terminateEndpoint(ref: ActorRef): Unit = {
    killActors(ref)
    CamelExtension(system).deactivationFutureFor(ref).toTry should be a 'success
  }

  def killActors(refs: ActorRef*): Unit = {
    val tp = TestProbe()
    refs.foreach { ref ⇒
      tp watch ref
      tp.send(ref, PoisonPill)
      tp.expectTerminated(ref)
    }
  }

  implicit class SourceOps[A](src: Source[A, ActorRef]) {
    def testProbe(f: TestSubscriber.Probe[A] => Unit): Unit = {
      val (ref, probe) = src.toMat(TestSink.probe(system))(Keep.both).run()
      try f(probe) finally terminateEndpoint(ref)
    }
  }

  def randomId = UUID.randomUUID.toString

  override protected def afterAll(): Unit = {
    system.terminate().toTry should be a 'success
  }
}