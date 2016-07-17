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
package io

import akka.stream._
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import akka.stream.stage._
import akka.util.ByteString
import akka.{ Done, NotUsed }

import scala.concurrent.Future
import scala.util.{ Success, Try }

final case class DigestResult(messageDigest: ByteString, status: Try[Done])

sealed trait Algorithm
object Algorithm {
  case object MD2 extends Algorithm
  case object MD5 extends Algorithm
  case object `SHA-1` extends Algorithm
  case object `SHA-256` extends Algorithm
  case object `SHA-384` extends Algorithm
  case object `SHA-512` extends Algorithm
}

/**
 * The DigestCalculator transforms/digests a stream of [[akka.util.ByteString]] to a
 * [[akka.stream.integration.io.DigestResult]] according to a given [[akka.stream.integration.io.Algorithm]]
 */
object DigestCalculator {

  def apply(algorithm: Algorithm): Flow[ByteString, DigestResult, NotUsed] =
    Flow.fromGraph[ByteString, DigestResult, NotUsed](new DigestCalculator(algorithm))

  def flow(algorithm: Algorithm): Flow[ByteString, DigestResult, NotUsed] =
    apply(algorithm)

  /**
   * Returns the String encoded as Hex representation of the digested stream of [[akka.util.ByteString]]
   */
  def hexString(algorithm: Algorithm): Flow[ByteString, String, NotUsed] =
    flow(algorithm).map(res => res.messageDigest.toArray.map("%02x".format(_)).mkString).fold("")(_ + _)

  def sink(algorithm: Algorithm): Sink[ByteString, Future[DigestResult]] =
    flow(algorithm).toMat(Sink.head)(Keep.right)

  def source(algorithm: Algorithm, text: String): Source[String, NotUsed] =
    Source.single(ByteString(text)).via(hexString(algorithm))
}

private[io] class DigestCalculator(algorithm: Algorithm) extends GraphStage[FlowShape[ByteString, DigestResult]] {
  val in: Inlet[ByteString] = Inlet("DigestCalculator.in")
  val out: Outlet[DigestResult] = Outlet("DigestCalculator.out")
  override val shape: FlowShape[ByteString, DigestResult] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val digest = java.security.MessageDigest.getInstance(algorithm.toString)

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val chunk = grab(in)
        digest.update(chunk.toArray)
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        emit(out, DigestResult(ByteString(digest.digest()), Success(Done)))
        completeStage()
      }
    })
  }
}