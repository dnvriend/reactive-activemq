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

package akka.stream.integration.extractor

import akka.camel.CamelMessage
import akka.stream.integration.{ CamelMessageExtractor, JsonCamelMessageExtractor, TestSpec }
import spray.json._

import scala.compat.Platform

object JsonMessageExtractorWithoutHeadersTest extends DefaultJsonProtocol {
  case class MessageReceived(fileName: String, timestamp: Long)
  implicit val messageReceivedJsonFormat = jsonFormat2(MessageReceived)
  implicit val jsonCamelMessageExtractor = JsonCamelMessageExtractor.jsonMessageExtractor[MessageReceived]
}

class JsonMessageExtractorWithoutHeadersTest extends TestSpec {
  import JsonMessageExtractorWithoutHeadersTest._

  it should "extract a message" in {
    val msg = MessageReceived("foo.txt", Platform.currentTime)
    val camelMessage = CamelMessage(msg.toJson.compactPrint, Map.empty)
    val extracted = implicitly[CamelMessageExtractor[MessageReceived]].extract(camelMessage)
    extracted shouldEqual msg
  }
}
