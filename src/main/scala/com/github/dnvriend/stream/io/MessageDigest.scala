package com.github.dnvriend.stream
package io

import akka.stream.{Attributes, Inlet, SinkShape}
import akka.stream.scaladsl.Sink
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.util.ByteString

import scala.concurrent.Future

object MessageDigest {
  def fromFile(file: String) = {
    Sink.fromGraph(new MD5DigestSink)
  }
}

class MD5DigestSink extends GraphStage[SinkShape[ByteString]] {
  val in: Inlet[ByteString] = Inlet("MS5DigestSink")
  override val shape: SinkShape[ByteString] = SinkShape(in)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = ???
}
