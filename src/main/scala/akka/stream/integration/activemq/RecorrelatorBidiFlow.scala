package akka.stream.integration.activemq

import akka.stream.integration.activemq.RecorrelatorBidiFlow.Correlator
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}

/**
  *
  */
object RecorrelatorBidiFlow {

  trait Correlator[C, I, O] {

    /**
      * Use this to extract an identifier from the request that can be used to correlate its response
      */
    def extractRequestCorrelation: I => C

    /**
      * Use this to extract from a response that can be used to correlate it to the original request
      */
    def correlateResponse: O => C
  }

  def apply[CIn, CMem, Req, Resp](correlator: Correlator[CMem, Req, Resp]): RecorrelatorBidiFlow[CIn, CMem, Req, Resp] = {
    new RecorrelatorBidiFlow[CIn, CMem, Req, Resp](correlator)
  }
}

/**
  *
  * This Recorrelator bidirectional flow allows one to link two flows with separate correlation-mechanisms. In the
  * intended use case, the back-end has a more business-oriented mechanism (some business-process-identifier with each
  * message), and the front-end a more technical one (the acknowledgment flow using actor-refs or promises to
  * acknowledge a request to ActiveMq).
  *
  * The Recorrelator flow expects a Correlator instance, which extracts a correlation identifier from the request (the
  * message that passes from front-end to back-end) and the response (the message passing from back-end to front-end).
  * This flow preserves both identifiers s.t. the front-end correlator can be provided to the front-end, along with
  * the response from the back-end.
  *
  * Important constraints:
  * - The back-end flow is expected to be ONE-ON-ONE: For each request, it will eventually provide a response. If no
  * response can be created, the back-end flow should terminate with error, or suffer that the internal buffer of the
  * Recorrelator will grow unbounded.
  *
  * @param correlator
  * @tparam CIn The correlation 'identifier' used on the front-end route
  * @tparam CMem The correlation identifier persisted
  * @tparam Req The request message type propagated from front-end to back-end
  * @tparam Resp The response message type propagated from back-end to front-end
  *
  * TODO:
  * - Set a(n optional) buffer limit
  * - Add TTL to messages in-transit
  * - Add a strategy for how to deal with TTLs: (crash-if-TTL-exceeded, crash-on-late-response, dispatch-empty-response vs. drop-request? request-without-response?)
  */
class RecorrelatorBidiFlow[CIn, CMem, Req, Resp](correlator: Correlator[CMem, Req, Resp]) extends GraphStage[BidiShape[(CIn, Req), Req, Resp, (CIn, Resp)]] {

  val in1: Inlet[(CIn, Req)] = Inlet[(CIn, Req)]("in1")
  val out1: Outlet[Req] = Outlet[Req]("out1")
  val in2: Inlet[Resp] = Inlet[Resp]("in1")
  val out2: Outlet[(CIn, Resp)] = Outlet[(CIn, Resp)]("out2")

  override def shape: BidiShape[(CIn, Req), Req, Resp, (CIn, Resp)] = BidiShape.of(in1, out1, in2, out2)

  @scala.throws[Exception](classOf[Exception])
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    var inTransit: Map[CMem, CIn] = Map.empty

    setHandler(in1, new InHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPush(): Unit = {
        val (cin, request) = grab(in1)
        inTransit += correlator.extractRequestCorrelation(request) -> cin
        push(out1, request)
      }

      @scala.throws[Exception](classOf[Exception])
      override def onUpstreamFinish(): Unit = complete(out1)
    })

    setHandler(out1, new OutHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPull(): Unit = if(!hasBeenPulled(in1)) tryPull(in1)

      @scala.throws[Exception](classOf[Exception])
      override def onDownstreamFinish(): Unit = cancel(in1)
    })

    setHandler(in2, new InHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPush(): Unit = {
        val response = grab(in2)
        val correlationId: CMem = correlator.correlateResponse(response)
        val maybeCin = inTransit.get(correlationId)
        inTransit -= correlationId

        maybeCin match {
          case None =>
            // This is probably a bit drastic
            failStage(new IllegalStateException(s"[$correlationId] Received response without corresponding request"))
          case Some(cin) =>
            push(out2, (cin, response))
        }

        if(inTransit.isEmpty && isClosed(in1))
          completeStage()
      }
    })

    setHandler(out2, new OutHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPull(): Unit = if(!hasBeenPulled(in1)) tryPull(in2)
    })
  }
}