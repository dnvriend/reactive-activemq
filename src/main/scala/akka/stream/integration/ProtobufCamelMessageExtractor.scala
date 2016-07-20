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
import com.google.protobuf.Message

import scalaz.Semigroup

trait ProtobufCamelMessageExtractor {
  implicit def protobufCamelMessageExtractor[Out: ProtobufReader](implicit headersExtractor: HeadersExtractor[Out] = null, semigroup: Semigroup[Out] = null) = new CamelMessageExtractor[Out] {
    override def extract(in: CamelMessage): Out = {
      val msg = in.body.asInstanceOf[Message]
      val out = implicitly[ProtobufReader[Out]].read(msg)
      (for {
        extractor <- Option(headersExtractor)
        semigroup <- Option(semigroup)
      } yield semigroup.append(out, extractor.extract(in.headers))).getOrElse(out)
    }
  }
}

object ProtobufCamelMessageExtractor extends ProtobufCamelMessageExtractor
