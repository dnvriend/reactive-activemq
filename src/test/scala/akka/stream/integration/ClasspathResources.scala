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

import java.io.InputStream

import akka.stream.IOResult
import akka.stream.scaladsl.{ Source, StreamConverters }
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.{ Source => ScalaIOSource }

trait ClasspathResources {
  def withInputStream[T](fileName: String)(f: InputStream => T): T = {
    val is: InputStream = fromClasspathAsStream(fileName)
    try f(is) finally is.close()
  }

  def withInputStreamAsText[T](fileName: String)(f: String => T): T =
    f(fromClasspathAsString(fileName))

  def withByteStringSource[T](fileName: String)(f: Source[ByteString, Future[IOResult]] => T): T =
    withInputStream(fileName) { inputStream =>
      f(StreamConverters.fromInputStream(() => inputStream))
    }

  def streamToString(is: InputStream): String =
    ScalaIOSource.fromInputStream(is).mkString

  def fromClasspathAsString(fileName: String): String =
    streamToString(fromClasspathAsStream(fileName))

  def fromClasspathAsStream(fileName: String): InputStream =
    getClass.getClassLoader.getResourceAsStream(fileName)
}
