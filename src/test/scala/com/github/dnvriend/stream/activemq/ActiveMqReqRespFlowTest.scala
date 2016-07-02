package com.github.dnvriend.stream.activemq

import com.github.dnvriend.stream.PersonDomain.Person

import scala.concurrent.Promise

/**
  *
  */
class ActiveMqReqRespFlowTest extends ActiveMqTestSpec {

  behavior of "ActiveMqReqRespFlow"

  it should "support request-response for a single message" in {
    withBackendFlow { implicit backendFlow ⇒ flowProbe ⇒
      withReqRespBidiFlow("AckBidiFlowReqRespTestInput") { testFlow ⇒
        withTestTopicPublisher("AckBidiFlowReqRespTestInput") { pub ⇒
          withTestTopicSubscriber("AckBidiFlowReqRespTestOutput") { sub ⇒

            // echo all received messages
            flowProbe.setAutoPilot(identity[Person] _)
            testFlow.join(backendFlow).run()

            sub.request(2)
            pub.sendNext(testPerson1)

            sub.expectNextPF {
              case (p: Promise[Unit], `testPerson1`) ⇒ p.success(())
            }

            sub.cancel()
            pub.sendComplete()
          }
        }
      }
    }
  }


  it should "support request-response for a multiple messages" in {
    withBackendFlow { implicit backendFlow ⇒ flowProbe ⇒
      withReqRespBidiFlow("AckBidiFlowReqRespTestInput") { testFlow ⇒
        withTestTopicPublisher("AckBidiFlowReqRespTestInput") { pub ⇒
          withTestTopicSubscriber("AckBidiFlowReqRespTestOutput") { sub ⇒

            // echo all received messages
            flowProbe.setAutoPilot(identity[Person] _)
            testFlow.join(backendFlow).run()

            sub.request(2)

            pub.sendNext(testPerson1)
            sub.expectNextPF {
              case (p: Promise[Unit], `testPerson1`) ⇒ p.success(())
            }

            pub.sendNext(testPerson2)
            sub.expectNextPF {
              case (p: Promise[Unit], `testPerson2`) ⇒ p.success(())
            }

            sub.cancel()
            pub.sendComplete()
          }
        }
      }
    }
  }
}
