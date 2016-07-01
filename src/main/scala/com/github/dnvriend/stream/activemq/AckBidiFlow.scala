package com.github.dnvriend.stream.activemq

import akka.actor.ActorSystem
import akka.camel.CamelMessage
import akka.stream._
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Keep}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.{Done, NotUsed}
import com.github.dnvriend.stream.camel.{MessageBuilder, MessageExtractor}

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}

/**
  * This is a naive implementation of a bidirectional flow from/to ActiveMq; it assumes:
  * - a 1 on 1 correspondence (bijection) between items sent from Out and received on In (see diagram)
  * - that ordering is preserved between Out and In (see diagram); i.e. no mapAsyncUnordered, ideally no network-
  * traversals; careful with dispatching to actors
  * - that at-least-once-delivery is acceptable on ActiveMqSink
  *
  * This flow is practical for the typical use case of handling a request received from activemq, processing it with
  * some bidi-flow, and dispatching a response to ActiveMq. The original requests gets acked once the response is sent.
  *
  * {{{
  *                  +-------------+
  * ActiveMqSource ~>|             |~> Out
  *                  | AckBidiFlow |
  * ActiveMqSink   <~|             |<~ In
  *                  +-------------+
  * }}}
  */
object AckBidiFlow {

  def apply[S, T](consumerName: String,
                  producerName: String,
                  qos: Int = 8,
                  queueSize: Int = 20)
                 (implicit ec: ExecutionContext,
                  system: ActorSystem,
                  extractor: MessageExtractor[CamelMessage, S],
                  builder: MessageBuilder[T, CamelMessage]): Flow[T, S, Future[Done]] = {

    val amqSource = ActiveMqSource.apply(consumerName)
    val amqSink = AckActiveMqSink.apply(producerName, qos)

    Flow.fromGraph(GraphDSL.create(amqSource, amqSink)(Keep.right) { implicit b ⇒ (source, sink) ⇒
      import GraphDSL.Implicits._

      val bidi = b.add(AckBidiFlow[S, T](queueSize))

      source ~> bidi.in1
      bidi.out2 ~> sink

      FlowShape(bidi.in2, bidi.out1)
    })
  }

  def apply[S, T](queueSize: Int): BidiFlow[AckTup[S], S, T, AckTup[T], NotUsed] =
    BidiFlow.fromGraph(new AckBidiFlow[S, T](queueSize))
}

class AckBidiFlow[S, T](queueSize: Int) extends GraphStage[BidiShape[AckTup[S], S, T, AckTup[T]]] {

  var queue: Queue[AckTup[S]] = Queue.empty

  val ackIn = Inlet[AckTup[S]]("AckBidiFlow.ackIn")
  val downOut = Outlet[S]("AckBidiFlow.downOut")
  val downIn = Inlet[T]("AckBidiFlow.downIn")
  val ackOut = Outlet[AckTup[T]]("AckBidiFlow.ackOut")

  override def shape: BidiShape[AckTup[S], S, T, AckTup[T]] = BidiShape.of(ackIn, downOut, downIn, ackOut)


  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    setHandler(ackIn, new InHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPush(): Unit = {
        val request = grab(ackIn)
        queue = queue.enqueue(request)
        push(downOut, request._2)
      }
    })

    setHandler(downOut, new OutHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPull(): Unit = {
        tryPull(ackIn)
      }
    })

    setHandler(downIn, new InHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPush(): Unit = {
        val response = grab(downIn)
        if (queue.isEmpty)
          failStage(new RuntimeException(s"Received element $response, but requests queue is empty"))

        val (ackRequest, newQueue) = queue.dequeue
        queue = newQueue

        val ackResponse = ackRequest.copy(_2 = response)
        push(ackOut, ackResponse)
      }
    })

    setHandler(ackOut, new OutHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPull(): Unit = tryPull(downIn)
    })
  }
}
