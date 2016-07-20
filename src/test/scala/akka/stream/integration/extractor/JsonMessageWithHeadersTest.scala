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
import akka.stream.integration.{ CamelMessageExtractor, HeadersExtractor, JsonCamelMessageExtractor, TestSpec }
import spray.json.DefaultJsonProtocol

import scalaz.Semigroup

object JsonMessageWithHeadersTest extends DefaultJsonProtocol {
  case class MessageReceived(fileName: String, header: Option[String], timestamp: Long)
  implicit val messageReceivedJsonFormat = jsonFormat3(MessageReceived)
  //
  // JsonCamelMessage extractor can extract headers given an HeaderExtractor
  // and a Semigroup to merge the two MessageReceived case classes
  //
  implicit val messageReceivedHeaderExtractor = new HeadersExtractor[MessageReceived] {
    override def extract(in: Map[String, Any]): MessageReceived =
      MessageReceived("", in.get("ORDER_ID").map(_.toString), 0)
  }
  // what to do when merging the two messages?
  implicit val messageReceivedSemigroup = new Semigroup[MessageReceived] {
    override def append(f1: MessageReceived, f2: => MessageReceived): MessageReceived =
      f1.copy(header = f2.header)
  }
  implicit val jsonCamelMessageExtractor = JsonCamelMessageExtractor.jsonMessageExtractor[MessageReceived]
}

class JsonMessageWithHeadersTest extends TestSpec {
  import JsonMessageWithHeadersTest._
  it should "extract a message with headers" in {
    val orderId = randomId
    val camelMessage = CamelMessage("""{"fileName":"test.txt", "timestamp":1}""", Map("ORDER_ID" -> orderId))
    val extracted = implicitly[CamelMessageExtractor[MessageReceived]].extract(camelMessage)
    extracted shouldEqual MessageReceived("test.txt", Some(orderId), 1)
  }
}
