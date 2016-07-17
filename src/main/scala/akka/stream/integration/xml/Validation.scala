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

import java.io.InputStream
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.util.{ ByteString, ByteStringBuilder }
import akka.{ Done, NotUsed }

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

final case class ValidationResult(status: Try[Done])

object Validation {
  def apply(xsd: String): Flow[ByteString, ValidationResult, NotUsed] =
    Flow.fromGraph(new ValidationFlow(xsd))

  def sink(xsd: String): Sink[ByteString, Future[ValidationResult]] =
    apply(xsd).toMat(Sink.head)(Keep.right)
}

private[xml] class ValidationFlow(xsd: String) extends GraphStage[FlowShape[ByteString, ValidationResult]] {
  val in: Inlet[ByteString] = Inlet("DigestCalculator.in")
  val out: Outlet[ValidationResult] = Outlet("DigestCalculator.out")
  override val shape: FlowShape[ByteString, ValidationResult] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val b = new ByteStringBuilder

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        b.append(grab(in))
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        try {
          val xsdStream: InputStream = getClass.getClassLoader.getResourceAsStream(xsd)
          val schemaLang = "http://www.w3.org/2001/XMLSchema"
          val factory = SchemaFactory.newInstance(schemaLang)
          val schema = factory.newSchema(new StreamSource(xsdStream))
          val validator = schema.newValidator()
          validator.validate(new StreamSource(b.result().iterator.asInputStream))
          emit(out, ValidationResult(Success(Done)))
        } catch {
          case t: Throwable =>
            emit(out, ValidationResult(Failure(t)))
        }
        completeStage()
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}
