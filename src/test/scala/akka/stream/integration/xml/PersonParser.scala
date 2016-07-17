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
package xml

import akka.NotUsed
import akka.stream.integration.PersonDomain.{ Address, Person }
import akka.stream.scaladsl.Flow
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

import scala.xml.pull.{ EvElemEnd, EvElemStart, EvText, XMLEvent }

object PersonParser {
  def apply(): Flow[XMLEvent, Person, NotUsed] = Flow.fromGraph(new PersonParser)
}

class PersonParser extends GraphStage[FlowShape[XMLEvent, Person]] {
  val in = Inlet[XMLEvent]("PersonParser.in")
  val out = Outlet[Person]("PersonParser.out")

  override def shape: FlowShape[XMLEvent, Person] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      var person = Person()
      var address = Address()
      val personHandler = new PersonHandler
      val addressHandler = new AddressHandler

      class PersonHandler extends InHandler {
        var inFirstName: Boolean = false
        var inLastName: Boolean = false
        var inAge: Boolean = false
        override def onPush(): Unit = grab(in) match {
          case EvElemEnd(_, "person") =>
            val personToEmit = person.copy(address = address)
            push(out, personToEmit)
            person = Person()
            address = Address()

          case EvElemStart(_, "first-name", _, _) =>
            inFirstName = true
            pull(in)

          case EvElemStart(_, "last-name", _, _) =>
            inLastName = true
            pull(in)

          case EvElemStart(_, "age", _, _) =>
            inAge = true
            pull(in)

          case EvText(text) if inFirstName =>
            person = person.copy(firstName = text)
            pull(in)

          case EvText(text) if inLastName =>
            person = person.copy(lastName = text)
            pull(in)

          case EvText(text) if inAge =>
            person = person.copy(age = text.toInt)
            pull(in)

          case EvElemEnd(_, "first-name") =>
            inFirstName = false
            pull(in)

          case EvElemEnd(_, "last-name") =>
            inLastName = false
            pull(in)

          case EvElemEnd(_, "age") =>
            inAge = false
            pull(in)

          case EvElemStart(_, "address", _, _) =>
            setHandler(in, addressHandler)
            pull(in)

          case _ =>
            pull(in)
        }
      }

      class AddressHandler extends InHandler {
        var inStreet: Boolean = false
        var inHouseNr: Boolean = false
        var inZip: Boolean = false
        var inCity: Boolean = false
        override def onPush(): Unit = grab(in) match {
          case EvElemEnd(_, "address") =>
            setHandler(in, personHandler)
            pull(in)

          case EvElemStart(_, "street", _, _) =>
            inStreet = true
            pull(in)

          case EvElemStart(_, "house-number", _, _) =>
            inHouseNr = true
            pull(in)

          case EvElemStart(_, "zip-code", _, _) =>
            inZip = true
            pull(in)

          case EvElemStart(_, "city", _, _) =>
            inCity = true
            pull(in)

          case EvElemEnd(_, "street") =>
            inStreet = false
            pull(in)

          case EvElemEnd(_, "house-number") =>
            inHouseNr = false
            pull(in)

          case EvElemEnd(_, "zip-code") =>
            inZip = false
            pull(in)

          case EvElemEnd(_, "city") =>
            inCity = false
            pull(in)

          case EvText(text) if inStreet =>
            address = address.copy(street = text)
            pull(in)

          case EvText(text) if inHouseNr =>
            address = address.copy(houseNumber = text)
            pull(in)

          case EvText(text) if inZip =>
            address = address.copy(zipCode = text)
            pull(in)

          case EvText(text) if inCity =>
            address = address.copy(city = text)
            pull(in)

          case _ =>
            pull(in)
        }
      }

      setHandler(in, personHandler)
      setHandler(out, new OutHandler {
        override def onPull(): Unit =
          pull(in)
      })
    }
}
