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

import scala.xml.pull.{ EvElemEnd, EvElemStart, EvText }

class XMLEventSourceTest extends TestSpec {
  it should "parse persons.xml" in {
    withTestXMLEventSource()(PersonsXmlFile) { tp =>
      tp.request(Int.MaxValue)
      tp.expectNext() should matchPattern { case EvElemStart(_, "persons", _, _) => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemStart(_, "person", _, _) => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemStart(_, "first-name", _, _) => }
      tp.expectNext() should matchPattern { case EvText("Barack") => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "first-name") => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemStart(_, "last-name", _, _) => }
      tp.expectNext() should matchPattern { case EvText("Obama") => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "last-name") => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemStart(_, "age", _, _) => }
      tp.expectNext() should matchPattern { case EvText("54") => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "age") => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemStart(_, "address", _, _) => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemStart(_, "street", _, _) => }
      tp.expectNext() should matchPattern { case EvText("Pennsylvania Ave") => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "street") => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemStart(_, "house-number", _, _) => }
      tp.expectNext() should matchPattern { case EvText("1600") => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "house-number") => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemStart(_, "zip-code", _, _) => }
      tp.expectNext() should matchPattern { case EvText("20500") => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "zip-code") => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemStart(_, "city", _, _) => }
      tp.expectNext() should matchPattern { case EvText("Washington") => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "city") => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "address") => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "person") => }
      tp.expectNext() should matchPattern { case EvText(_) => }
      tp.expectNext() should matchPattern { case EvElemEnd(_, "persons") => }
      tp.expectComplete()
    }
  }
}
