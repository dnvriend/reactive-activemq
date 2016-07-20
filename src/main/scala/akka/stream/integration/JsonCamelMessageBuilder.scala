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

import akka.camel.CamelMessage
import spray.json.JsonWriter
import spray.json._

trait JsonCamelMessageBuilder {
  implicit def jsonMessageBuilder[In: JsonWriter](implicit headersBuilder: HeadersBuilder[In] = null) = new CamelMessageBuilder[In] {
    override def build(in: In): CamelMessage = {
      val jsonString: String = in.toJson.compactPrint
      val headers: Map[String, Any] = Option(headersBuilder).map(_.build(in)).getOrElse(Map.empty[String, Any])
      CamelMessage(jsonString, headers)
    }
  }
}

object JsonCamelMessageBuilder extends JsonCamelMessageBuilder
