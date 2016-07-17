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
import akka.stream._
import akka.stream.scaladsl.{ BidiFlow, Flow, GraphDSL, Unzip, Zip }

object AckBidiFlow {

  /**
   * Create a basic bi-directional flow that bridges between an acknowledging flow on the FRONT-END and
   * an 'ordered bijection' on the BACK-END
   *
   * @param bufferSize The size of the buffer in element count; be aware that stages have internal buffers either way
   * @param overflowStrategy Strategy that is used when incoming elements cannot fit inside the buffer
   * @tparam R The request-acknowledgement type: i.e. a response for request-response pattern, or Unit to Ack the request
   * @tparam S The source element type
   * @tparam T The output element type to dispatch to the sink
   * @return
   */
  def apply[R, S, T](
    bufferSize: Int = 10,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure
  ): BidiFlow[(R, S), S, T, (R, T), NotUsed] = {

    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val unzip = b.add(Unzip[R, S])
      val zip = b.add(Zip[R, T])

      if (bufferSize > 0) {
        val buffer = b.add(Flow[R].buffer(bufferSize, overflowStrategy))
        unzip.out0 ~> buffer ~> zip.in0
      } else
        unzip.out0 ~> zip.in0

      BidiShape(unzip.in, unzip.out1, zip.in1, zip.out)
    })
  }
}
