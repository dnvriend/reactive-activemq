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

import java.io.{ BufferedInputStream, File, FileInputStream, InputStream }

import akka.NotUsed
import akka.stream.scaladsl.Source

import scala.io.{ Source => ScalaIOSource }
import scala.xml.pull.{ XMLEvent, XMLEventReader }

object XMLEventSource {
  def fromInputStream(xml: InputStream): Source[XMLEvent, NotUsed] =
    Source.fromIterator(() => new XMLEventReader(ScalaIOSource.fromInputStream(xml)))

  def fromFile(file: File): Source[XMLEvent, NotUsed] =
    fromInputStream(new BufferedInputStream(new FileInputStream(file)))

  def fromFileName(name: String): Source[XMLEvent, NotUsed] =
    fromInputStream(new BufferedInputStream(new FileInputStream(name)))

  def validation(xsd: String) = ???

}
