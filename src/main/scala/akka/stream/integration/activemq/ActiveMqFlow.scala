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
import akka.stream.FlowShape
import akka.stream.integration.activemq.RecorrelatorBidiFlow.Correlator
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.{ExecutionContext, Promise}

/**
 * This is an implementation of a bi-directional flow, with the use case of bridging between the (FRONT-END)
 * interfacing stages and the (BACK-END) application flow. The front-end has a technical way of linking metadata to
 * each request, such that the sink can acknowledge it. This bi-directional flow introduces some separation of concerns
 * in that it hides that internal mechanism such that your application interface does not need to account for it.
 *
 * Of course, this introduces alternative constraints. Look at the particular constructor implementation for details.
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
    * hooked up to `sink`. This constructor is intended for cases where the application behaves 'function-like'. This
    * can be summarized as the following two properties:
    * - ONE-ON-ONE: Each request needs exactly one response. This constraint exists with or without the addition of the
    * bidirectional flow, but is enforced with the introduction of this bidi-flow. Not respecting the rule gives a
    * guaranteed inconsistency w.r.t. the responses sent
    * - ORDER-PRESERVING: For each two elements x,y dispatched to the BACK-END, if x happened-before y, the
    * applications' response x' on x and y' on y should have x' ordered before y'
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
    * Create an acknowledging bi-directional flow, with its FRONT-END-in hooked up to `source` and its FRONT-END-out
    * hooked up to `sink`. This constructor is intended for use cases where ordering of the BACK-END is not guaranteed.
    * This bidi-flow does not restore the ordering, but its consistency is not threatened by it either.
    *
    * To do its work, it requires a correlator that extracts a correlation-identifier from the request and response.
    * An internal buffer allows for the reconstruction of the original metadata with the response such that the FRONT-
    * END knows how to acknowledge. No two messages in-transit may define the same correlation-identifier, as this will
    * obviously threaten consistency. It is good practice to take something that is close to guaranteed to be unique,
    * but once a request is handled, the correlation-id is effectively free for reuse.
    */
  def apply[C, S, T, M1, M2](source: Source[AckUTup[S], M1], sink: Sink[AckUTup[T], M2], correlator: Correlator[C, S, T])(implicit ec: ExecutionContext, system: ActorSystem): Flow[T, S, NotUsed] = {
    applyMat(source, sink, correlator)(Keep.none)
  }

  def applyMat[C, S, T, M1, M2, Mat](source: Source[AckUTup[S], M1], sink: Sink[AckUTup[T], M2], correlator: Correlator[C, S, T])(combineMat: (M1, M2) => Mat)(implicit ec: ExecutionContext, system: ActorSystem): Flow[T, S, Mat] = {

    Flow.fromGraph(GraphDSL.create(source, sink)(combineMat) { implicit b => (src, snk) =>
      import GraphDSL.Implicits._

      val bidi = b.add(RecorrelatorBidiFlow[Promise[Unit], C, S, T](correlator))

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
