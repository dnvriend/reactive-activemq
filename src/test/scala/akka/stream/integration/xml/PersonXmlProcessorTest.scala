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

import scala.concurrent.duration._

class PersonXmlProcessorTest extends TestSpec {
  it should "process full person" in {
    withTestXMLPersonParser()(PersonsXmlFile) { tp =>
      tp.request(1)
      tp.expectNext(testPerson1)
      tp.expectNoMsg(100.millis)
    }
  }

  it should "count a lot of persons" in {
    withInputStream(LotOfPersonsXmlFile) { is =>
      XMLEventSource.fromInputStream(is)
        .via(PersonParser())
        .runFold(0) { case (c, _) => c + 1 }
        .futureValue shouldBe 4400
    }
  }
}
