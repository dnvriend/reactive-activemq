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

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.stream.stage._
import akka.util.ByteString

import scala.concurrent.Future

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

  def apply(algorithm: Algorithm): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph[ByteString, ByteString, NotUsed](new DigestCalculator(algorithm))

  def flow(algorithm: Algorithm): Flow[ByteString, ByteString, NotUsed] =
    apply(algorithm)

  def hexString(algorithm: Algorithm): Flow[ByteString, String, NotUsed] =
    flow(algorithm).map(arr ⇒ arr.toArray.map("%02x".format(_)).mkString).fold("")(_ + _)

  def sink(algorithm: Algorithm): Sink[ByteString, Future[ByteString]] =
    flow(algorithm).toMat(Sink.head)(Keep.right)
}

class DigestCalculator(algorithm: Algorithm) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in: Inlet[ByteString] = Inlet("DigestCalculator.in")
  val out: Outlet[ByteString] = Outlet("DigestCalculator.out")
  override val shape: FlowShape[Digest, Digest] = FlowShape.of(in, out)

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
        emit(out, ByteString(digest.digest()))
        completeStage()
      }
    })
  }
}

//class DigestCalculator(algorithm: Algorithm) extends GraphStageWithMaterializedValue[SinkShape[ByteString], Future[DigestResult]] {
//  val in: Inlet[ByteString] = Inlet("DigestCalculator.in")
//  override val shape: SinkShape[ByteString] = SinkShape(in)
//
//  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[DigestResult]) = {
//    val promise = Promise[DigestResult]
//    val logic = new GraphStageLogic(shape) {
//      val m = java.security.MessageDigest.getInstance(algorithm.code)
//
//      setHandler(in, new InHandler {
//        @scala.throws[Exception](classOf[Exception])
//        override def onPush(): Unit = {
//          pull(in)
//          val chunk = grab(in)
//          m.update(chunk.toArray)
//        }
//
//        @scala.throws[Exception](classOf[Exception])
//        override def onUpstreamFinish(): Unit = {
//          promise.success(DigestResult(ByteString(m.digest()), Success(Done)))
//          super.onUpstreamFinish()
//        }
//
//        @scala.throws[Exception](classOf[Exception])
//        override def onUpstreamFailure(ex: Throwable): Unit = {
//          promise.failure(ex)
//          super.onUpstreamFailure(ex)
//        }
//      })
//    }
//    (logic, promise.future)
//  }
//}
