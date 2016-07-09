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

package akka.persistence.stream

import akka.stream.integration.TestSpec
import akka.stream.scaladsl.{ Flow, Sink, Source }
import scala.collection.immutable.Seq

class ResumableQuerySourceTest extends TestSpec {
  def withQueryFromOffset[A](matSink: Sink[Any, A] = Sink.ignore)(f: Flow[Any, Any, A] ⇒ Unit): Unit = {
    f(ResumableQuery("q1", offset ⇒ journal.eventsByTag("foo", offset + 1), matSink = matSink))
  }

  it should "resume from the last offset" in {
    Source.repeat("foo").take(10)
      .zip(Source.fromIterator(() ⇒ Iterator from 1)).map {
        case (a, b) ⇒ s"$a-$b"
      }.via(Journal(_ ⇒ Set("foo"))).runWith(Sink.ignore).futureValue

    withQueryFromOffset(Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((1, "foo-1"), (2, "foo-2"))
    }
    withQueryFromOffset(Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((3, "foo-3"), (4, "foo-4"))
    }
    withQueryFromOffset(Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((5, "foo-5"), (6, "foo-6"))
    }
  }
}
