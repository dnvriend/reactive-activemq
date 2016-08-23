package akka.stream.integration.activemq

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.integration.PersonDomain.Person
import akka.stream.integration._
import akka.stream.integration.activemq.RecorrelatorBidiFlow.Correlator
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.TestPublisher.Probe
import akka.stream.testkit.TestSubscriber.Probe
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.stream.testkit.{TestPublisher, TestSubscriber}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Promise}

/**
  *
  */
class RecorrelatorTest extends ActiveMqTestSpec {

  import RecorrelatorTest._

  behavior of "Recorrelator"

  it should "perform a round-trip" in {
    withBackEnd[IdentifiedMessage, IdentifiedResponse] { implicit backendFlow =>
      withRecorrelatorBidiFlow(defaultCorrelator) { feInProbe => beInProbe => beOutProbe => feOutProbe =>

        val inputPromise = Promise[Unit]()
        val inputMessage = IdentifiedMessage()
        val outputMessage = IdentifiedResponse(inputMessage.id)

        beInProbe.ensureSubscription()
        feOutProbe.ensureSubscription()

        feInProbe.sendNext((inputPromise, inputMessage))

        beInProbe.expectNoMsg(100.milliseconds)
        beInProbe.requestNext(inputMessage)

        beOutProbe.sendNext(outputMessage)

        feOutProbe.expectNoMsg(100.milliseconds)
        feOutProbe.requestNext((inputPromise, outputMessage))

        beInProbe.expectNoMsg(100.milliseconds)
        feOutProbe.expectNoMsg(100.milliseconds)

        feInProbe.sendComplete()
        beInProbe.expectComplete()

        feOutProbe.cancel()
        beOutProbe.expectCancellation()
      }
    }
  }

  it should "handle multiple messages" in {
    withBackEnd[IdentifiedMessage, IdentifiedResponse] { implicit backendFlow =>
      withRecorrelatorBidiFlow(defaultCorrelator) { feInProbe => beInProbe => beOutProbe => feOutProbe =>

        val inputPromise1 = Promise[Unit]()
        val inputPromise2 = Promise[Unit]()
        val inputMessage1 = IdentifiedMessage()
        val inputMessage2 = IdentifiedMessage()
        val outputMessage1 = IdentifiedResponse(inputMessage1.id)
        val outputMessage2 = IdentifiedResponse(inputMessage2.id)

        beInProbe.ensureSubscription()
        feOutProbe.ensureSubscription()

        feInProbe.sendNext((inputPromise1, inputMessage1))
        feInProbe.sendNext((inputPromise2, inputMessage2))

        beInProbe.expectNoMsg(100.milliseconds)
        beInProbe.requestNext(inputMessage1)

        beOutProbe.sendNext(outputMessage1)

        feOutProbe.expectNoMsg(100.milliseconds)
        feOutProbe.requestNext((inputPromise1, outputMessage1))


        beInProbe.expectNoMsg(100.milliseconds)
        feOutProbe.expectNoMsg(100.milliseconds)

        beInProbe.requestNext(inputMessage2)
        beOutProbe.sendNext(outputMessage2)
        feOutProbe.requestNext((inputPromise2, outputMessage2))

        beInProbe.cancel()
        feInProbe.expectCancellation()

        beOutProbe.sendComplete()
        feOutProbe.expectComplete()
      }
    }
  }

  it should "handle multiple messages, even if order is disturbed" in {
    withBackEnd[IdentifiedMessage, IdentifiedResponse] { implicit backendFlow =>
      withRecorrelatorBidiFlow(defaultCorrelator) { feInProbe => beInProbe => beOutProbe => feOutProbe =>

        val inputPromise1 = Promise[Unit]()
        val inputPromise2 = Promise[Unit]()
        val inputMessage1 = IdentifiedMessage()
        val inputMessage2 = IdentifiedMessage()
        val outputMessage1 = IdentifiedResponse(inputMessage1.id)
        val outputMessage2 = IdentifiedResponse(inputMessage2.id)

        // propagate everything from front-end to back-end
        beInProbe.ensureSubscription()
        feOutProbe.ensureSubscription()

        feInProbe.sendNext((inputPromise1, inputMessage1))
        feInProbe.sendNext((inputPromise2, inputMessage2))
        feInProbe.sendComplete()

        beInProbe.requestNext(inputMessage1)
        beInProbe.requestNext(inputMessage2)
        beInProbe.expectComplete()

        // and back, but changed order
        beOutProbe.sendNext(outputMessage2)
        beOutProbe.sendNext(outputMessage1)
        beOutProbe.sendComplete()

        feOutProbe.requestNext((inputPromise2, outputMessage2))
        feOutProbe.requestNext((inputPromise1, outputMessage1))
        feOutProbe.expectComplete()
      }
    }
  }

}

object RecorrelatorTest {

  case class IdentifiedMessage(id: UUID = UUID.randomUUID())
  case class IdentifiedResponse(id: UUID = UUID.randomUUID())

  def withRecorrelatorBidiFlow[C, I, O](correlator: Correlator[C, I, O])
                              (test: TestPublisher.Probe[AckUTup[I]] => TestSubscriber.Probe[I] => TestPublisher.Probe[O] => TestSubscriber.Probe[AckUTup[O]] => Any)
                              (implicit backendFlow: Flow[I, O, (TestSubscriber.Probe[I], TestPublisher.Probe[O])], system: ActorSystem, ec: ExecutionContext, mat: Materializer): Unit = {

    val inputSource = TestSource.probe[AckUTup[I]]
    val outputSink = TestSink.probe[AckUTup[O]]

    val partialTestFlow: Flow[O, I, (TestPublisher.Probe[AckUTup[I]], TestSubscriber.Probe[AckUTup[O]])] =
      ActiveMqFlow.applyMat(inputSource, outputSink, correlator)(Keep.both)

    val ((feInProbe, feOutProbe), (beInProbe, beOutProbe)) = partialTestFlow.joinMat(backendFlow)(Keep.both).run()

    test(feInProbe)(beInProbe)(beOutProbe)(feOutProbe)
  }

  def withBackEnd[I, O](test: Flow[I, O, (TestSubscriber.Probe[I], TestPublisher.Probe[O])] => Any)(implicit system: ActorSystem): Unit = {
    val backEndSink: Sink[I, TestSubscriber.Probe[I]] = TestSink.probe[I]
    val backEndSource: Source[O, TestPublisher.Probe[O]] = TestSource.probe[O]
    val flow: Flow[I, O, (TestSubscriber.Probe[I], TestPublisher.Probe[O])] = Flow.fromSinkAndSourceMat(backEndSink, backEndSource)(Keep.both)
    test(flow)
  }

  val defaultCorrelator: Correlator[UUID, IdentifiedMessage, IdentifiedResponse] = new Correlator[UUID, IdentifiedMessage, IdentifiedResponse] {
    /**
      * Use this to extract an identifier from the request that can be used to correlate its response
      */
    override def extractRequestCorrelation: IdentifiedMessage => UUID = _.id

    /**
      * Use this to extract from a response that can be used to correlate it to the original request
      */
    override def correlateResponse: IdentifiedResponse => UUID = _.id
  }

}