package com.github.dnvriend.stream.activemq

import akka.Done
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.testkit.TestProbe
import com.github.dnvriend.stream.PersonDomain.Person
import com.github.dnvriend.stream.TestSpec
import com.github.dnvriend.stream.camel.JsonMessageBuilder._
import com.github.dnvriend.stream.camel.JsonMessageExtractor._

import scala.concurrent.{Future, Promise}


class AckBidiFlowTest extends TestSpec {

  behavior of "AckBidiFlow"

  it should "propagate an element downstream, and propagate returned elements upstream, wrapped with the initial promise" in {
    withAckBidiFlow { inputProbe ⇒ flowProbe ⇒ outputProbe ⇒

      val inputPromise = Promise[Unit]()

      inputProbe.sendNext((inputPromise, testPerson1))

      flowProbe.expectNoMsg()

      outputProbe.request(1)

      flowProbe.expectMsg(testPerson1)

      outputProbe.expectNoMsg()

      flowProbe.reply(testPerson2)

      val outputPromise = outputProbe.expectNextPF { case (p: Promise[Unit], `testPerson2`) ⇒ p }

      inputPromise should equal(outputPromise)
    }
  }

  it should "respect buffer size" in {
    withAckBidiFlow { inputProbe ⇒ flowProbe ⇒ outputProbe ⇒

      val inputPromise1 = Promise[Unit]()
      val inputPromise2 = Promise[Unit]()
      inputProbe.sendNext((inputPromise1, testPerson1))
      inputProbe.sendNext((inputPromise2, testPerson2))

      outputProbe.request(2)

      flowProbe.expectMsg(testPerson1)

      // assert that buffer-size of 1 is respected
      flowProbe.expectNoMsg()
    }
  }

  it should "propagate messages from input to output unmodified, if mediated by the identity flow" in {
    withTestTopicPublisher("AckBidiFlowTestInput") { pub ⇒
      withTestTopicSubscriber("AckBidiFlowTestOutput") { sub ⇒
        withActiveMqBidiFlow("AckBidiFlowTestInput", "AckBidiFlowTestOutput") { flow ⇒

          val identityFlow = Flow[Person].map(identity)
          val program: Future[Done] = flow.join(identityFlow).run()

          pub.sendNext(testPerson1)

          sub.request(2)
          sub.expectNextPF {
            case (p: Promise[Unit], `testPerson1`) ⇒ p.success(())
          }

          pub.sendNext(testPerson2)
          pub.sendComplete()

          sub.expectNextPF {
            case (p: Promise[Unit], `testPerson2`) ⇒ p.success(())
          }
          sub.cancel()
        }
      }
    }
  }


  /**
    * Creates an acknowledging bidirectional flow whose input, back-end and output can be manipulated using probes
    */
  def withAckBidiFlow(test: TestPublisher.Probe[AckTup[Person]] ⇒ TestProbe ⇒ TestSubscriber.Probe[AckTup[Person]] ⇒ Any): Unit = {
    import akka.pattern.ask
    val flowProbe = TestProbe()

    val inputSource = TestSource.probe[AckTup[Person]]
    val outputSink = TestSink.probe[AckTup[Person]]
    val backendFlow = Flow[Person].mapAsync(1)(p ⇒ (flowProbe.ref ? p).mapTo[Person])

    val partialTestFlow = Flow.fromGraph(GraphDSL.create(inputSource, outputSink)((_, _)) { implicit b ⇒ (source, sink) ⇒
      import GraphDSL.Implicits._

      val bidi = b.add(AckBidiFlow[Person, Person](1))

      source ~> bidi.in1
      bidi.out2 ~> sink

      FlowShape(bidi.in2, bidi.out1)
    })

    val (inputProbe, outputProbe) = partialTestFlow.join(backendFlow).run()

    test(inputProbe)(flowProbe)(outputProbe)
  }


  /**
    * Creates an acknowledging bidirectional flow, connected with an ActiveMqSource and ActiveMqSink
    *
    * NOTE: Test using this fixture assume correct implementation of ActiveMqSource and ActiveMqSink!
    */
  def withActiveMqBidiFlow[S, T](sourceEndpoint: String, sinkEndpoint: String)
                                (test: Flow[Person, Person, Future[Done]] ⇒ Any): Unit =
    test(AckBidiFlow[Person, Person](sourceEndpoint, sinkEndpoint))
}
