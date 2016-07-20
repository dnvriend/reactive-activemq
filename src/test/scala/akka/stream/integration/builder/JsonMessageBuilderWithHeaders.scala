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

package akka.stream.integration.builder

import akka.camel.CamelMessage
import akka.stream.integration.{ CamelMessageBuilder, HeadersBuilder, JsonCamelMessageBuilder, TestSpec }
import spray.json.DefaultJsonProtocol

object JsonMessageBuilderWithHeaders extends DefaultJsonProtocol {
  case class MessageReceived(fileName: String, orderId: Option[String], timestamp: Long)
  implicit val messageReceivedJsonFormat = jsonFormat3(MessageReceived)
  //
  // JsonCamelMessageBuilder can optionally add headers to the CamelMessage
  // that it extracts from the case class ie. MessageReceived
  //
  implicit val messageReceivedHeadersBuilder = new HeadersBuilder[MessageReceived] {
    override def build(in: MessageReceived): Map[String, Any] =
      Map.empty[String, Any] ++ in.orderId.map("ORDER_ID" -> _)
  }
  implicit val messageReceivedJsonCamelMessageBuilder = JsonCamelMessageBuilder.jsonMessageBuilder[MessageReceived]
}

class JsonMessageBuilderWithHeaders extends TestSpec {
  import JsonMessageBuilderWithHeaders._
  it should "build a CamelMessage with headers" in {
    val orderId = randomId
    val msg = MessageReceived("fileName.txt", Option(orderId), 1)
    val camelMessage = implicitly[CamelMessageBuilder[MessageReceived]].build(msg)
    camelMessage shouldEqual CamelMessage(msg.toJson.compactPrint, Map("ORDER_ID" -> orderId))
  }
}
