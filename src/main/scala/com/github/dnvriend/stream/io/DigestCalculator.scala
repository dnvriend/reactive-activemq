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

package com.github.dnvriend.stream
package io

import akka.{ Done, NotUsed }
import akka.stream._
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.stream.stage._
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.Success

sealed trait Algorithm
object Algorithm {
  case object MD2 extends Algorithm
  case object MD5 extends Algorithm
  case object `SHA-1` extends Algorithm
  case object `SHA-256` extends Algorithm
  case object `SHA-384` extends Algorithm
  case object `SHA-512` extends Algorithm
}

object DigestCalculator {

  def apply(algorithm: Algorithm): Flow[ByteString, DigestResult, NotUsed] =
    Flow.fromGraph[ByteString, DigestResult, NotUsed](new DigestCalculator(algorithm))

  def flow(algorithm: Algorithm): Flow[ByteString, DigestResult, NotUsed] =
    apply(algorithm)

  def hexString(algorithm: Algorithm): Flow[ByteString, String, NotUsed] =
    flow(algorithm).map(res ⇒ res.messageDigest.toArray.map("%02x".format(_)).mkString).fold("")(_ + _)

  def sink(algorithm: Algorithm): Sink[ByteString, Future[DigestResult]] =
    flow(algorithm).toMat(Sink.head)(Keep.right)
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