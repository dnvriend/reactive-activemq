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
import scala.concurrent.Future

/**
 * There is *no* such thing as exactly-once-delivery!
 * Please make sure all processes can handle versioning of messages
 * Drop messages that are older
 */
class ResumableQuerySourceTest extends TestSpec {
  def withQueryFromOffset[A](queryName: String, matSink: Sink[Any, A] = Sink.ignore)(f: Flow[Any, Any, A] ⇒ Unit): Unit = {
    f(ResumableQuery(queryName, offset ⇒ journal.eventsByTag("foo", offset + 1), matSink = matSink))
  }

  def countEvents(queryName: String): Future[Long] =
    journal.currentEventsByPersistenceId(queryName, 0, Long.MaxValue).runWith(Sink.seq).map(_.size)

  "single query" should "resume from the last offset" in {
    Source.repeat("foo").take(10)
      .zip(Source.fromIterator(() ⇒ Iterator from 1)).map {
        case (a, b) ⇒ s"$a-$b"
      }.via(Journal(_ ⇒ Set("foo"))).runWith(Sink.ignore).futureValue

    withQueryFromOffset("q1", Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((1, "foo-1"), (2, "foo-2"))
    }

    eventually(countEvents("q1").futureValue shouldBe 2)

    withQueryFromOffset("q1", Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((3, "foo-3"), (4, "foo-4"))
    }

    eventually(countEvents("q1").futureValue shouldBe 4)

    withQueryFromOffset("q1", Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((5, "foo-5"), (6, "foo-6"))
    }

    eventually(countEvents("q1").futureValue shouldBe 6)
  }

  "multiple queries" should "resume from the last offset" in {
    Source.repeat("foo").take(10)
      .zip(Source.fromIterator(() ⇒ Iterator from 1)).map {
        case (a, b) ⇒ s"$a-$b"
      }.via(Journal(_ ⇒ Set("foo"))).runWith(Sink.ignore).futureValue

    withQueryFromOffset("q1", Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((1, "foo-1"), (2, "foo-2"))
    }

    eventually(countEvents("q1").futureValue shouldBe 2)

    withQueryFromOffset("q2", Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((1, "foo-1"), (2, "foo-2"))
    }

    eventually(countEvents("q2").futureValue shouldBe 2)

    withQueryFromOffset("q1", Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((3, "foo-3"), (4, "foo-4"))
    }

    eventually(countEvents("q1").futureValue shouldBe 4)

    withQueryFromOffset("q1", Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((5, "foo-5"), (6, "foo-6"))
    }

    eventually(countEvents("q1").futureValue shouldBe 6)

    withQueryFromOffset("q2", Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((3, "foo-3"), (4, "foo-4"))
    }

    eventually(countEvents("q2").futureValue shouldBe 4)

    withQueryFromOffset("q2", Sink.seq) { flow ⇒
      flow.join(Flow[Any].take(2)).run().futureValue shouldBe Seq((5, "foo-5"), (6, "foo-6"))
    }

    eventually(countEvents("q2").futureValue shouldBe 6)
  }
}
