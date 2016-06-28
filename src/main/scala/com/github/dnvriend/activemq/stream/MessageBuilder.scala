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

package com.github.dnvriend.activemq.stream

import akka.camel.CamelMessage
import spray.json.JsonWriter

trait MessageBuilder[A, CamelMessage] {
  def build(a: A): CamelMessage
}

object MessageBuilder {
  implicit val NoHeadersStringMessageBuilder = new MessageBuilder[String, CamelMessage] {
    override def build(body: String): CamelMessage =
      CamelMessage(body, Map.empty)
  }
}

trait JsonMessageBuilder {
  implicit def jsonMessageBuilder[A: JsonWriter] = new MessageBuilder[A, CamelMessage] {
    import spray.json._
    override def build(a: A): CamelMessage =
      implicitly[MessageBuilder[String, CamelMessage]].build(a.toJson.compactPrint)
  }
}

object JsonMessageBuilder extends JsonMessageBuilder