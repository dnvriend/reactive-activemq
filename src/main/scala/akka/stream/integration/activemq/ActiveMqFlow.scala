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
package activemq

import akka.NotUsed
import akka.actor.ActorSystem
import akka.camel.CamelMessage
import akka.stream.FlowShape
import akka.stream.scaladsl.{ Flow, GraphDSL, Keep, Sink, Source }

import scala.concurrent.{ ExecutionContext, Promise }

/**
 * This is a naive implementation of a bidirectional flow from/to ActiveMq; it assumes:
 * - that a 1 on 1 correspondence (bijection) exists between elements sent to - and received from the BACK-END
 * - that ordering is preserved between Out and In (see diagram); i.e. no mapAsyncUnordered, no foreachParallel,
 * ideally no network-traversals; careful with dispatching to actors, etc. If this property cannot be upheld due to
 * some stream-element's processing failing in an unexpected way, one will have to fail the graph
 * - that at-least-once-delivery is acceptable on ActiveMqSink
 *
 * This flow is practical for the typical use case of handling a request received from activemq, processing it with
 * some bidi-flow, and dispatching a response to ActiveMq. The original requests gets acked once the response is sent.
 *
 * {{{
 *
 * LEGEND:
 *
 * FRONT-END                          BACK-END
 *                  +-------------+
 * ActiveMqSource ~>|             |~> Out
 *                  | AckBidiFlow |
 * ActiveMqSink   <~|             |<~ In
 *                  +-------------+
 * }}}
 */
object ActiveMqFlow {

  /**
   * Create an acknowledging bi-directional flow, with its FRONT-END-in hooked up to `source` and its FRONT-END-out
   * hooked up to `sink`. This constructor is intended for cases where responses are sent to a fixed ActiveMq
   * destination (i.e. the Sink)
   */
  def apply[S, T, M1, M2](source: Source[AckUTup[S], M1], sink: Sink[AckUTup[T], M2])(implicit ec: ExecutionContext, system: ActorSystem): Flow[T, S, NotUsed] = {
    applyMat(source, sink)(Keep.none)
  }

  def applyMat[S, T, M1, M2, Mat](source: Source[AckUTup[S], M1], sink: Sink[AckUTup[T], M2])(combineMat: (M1, M2) => Mat)(implicit ec: ExecutionContext, system: ActorSystem): Flow[T, S, Mat] = {

    Flow.fromGraph(GraphDSL.create(source, sink)(combineMat) { implicit b => (src, snk) =>
      import GraphDSL.Implicits._

      val bidi = b.add(AckBidiFlow[Promise[Unit], S, T]())

      src ~> bidi.in1
      bidi.out2 ~> snk

      FlowShape(bidi.in2, bidi.out1)
    })
  }

  /**
   * Create a bidi-flow that is linked up to an ActiveMqSource and ActiveMqSink by their configuration name
   */
  def apply[S: CamelMessageExtractor, T: CamelMessageBuilder](consumerName: String, producerName: String, qos: Int = 8)(implicit ec: ExecutionContext, system: ActorSystem): Flow[T, S, NotUsed] =
    ActiveMqFlow(ActiveMqConsumer[S](consumerName), AckActiveMqProducer[T](producerName, qos))
}
