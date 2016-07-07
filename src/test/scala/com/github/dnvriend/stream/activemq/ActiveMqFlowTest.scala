package com.github.dnvriend.stream.activemq

import akka.stream.scaladsl.Flow
import com.github.dnvriend.stream.PersonDomain.Person

import scala.concurrent.Promise

/**
  *
  */
class ActiveMqFlowTest extends ActiveMqTestSpec {

  behavior of "ActiveMqFlow"

  it should "propagate messages from input to output unmodified, if mediated by the identity flow" in {
    withTestTopicPublisher("AckBidiFlowTestInput") { pub ⇒
      withTestTopicSubscriber("AckBidiFlowTestOutput") { sub ⇒
        withActiveMqBidiFlow("AckBidiFlowTestInput", "AckBidiFlowTestOutput") { flow ⇒

          val identityFlow = Flow[Person].map(identity)
          flow.join(identityFlow).run()

          pub.sendNext(testPerson1)

          sub.request(2)
          sub.expectNextPF {
            case (p: Promise[Unit], `testPerson1`) ⇒ p.success(())
          }

          pub.sendNext(testPerson2)

          sub.expectNextPF {
            case (p: Promise[Unit], `testPerson2`) ⇒ p.success(())
          }

          pub.sendComplete()
          sub.cancel()
        }
      }
    }
  }
}
