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

import akka.Done
import akka.persistence.query.EventEnvelope
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
  def withQueryFromOffset(queryName: String)(f: Flow[Any, EventEnvelope, Future[Done]] ⇒ Unit): Unit = {
    f(ResumableQuery(queryName, offset ⇒ journal.eventsByTag("foo", offset + 1)))
  }

  override def enableClearQueus: Boolean = false

  def countEvents(queryName: String): Future[Long] =
    journal.currentEventsByPersistenceId(queryName, 0, Long.MaxValue).runWith(Sink.seq).map(_.size)

  val takeTwoMapToUnit = Flow[EventEnvelope].take(2).map(_ ⇒ ())
  val takeTwo = Flow[EventEnvelope].take(2)

  "single query" should "resume from the last offset" in {
    Source.repeat("foo").take(10)
      .zip(Source.fromIterator(() ⇒ Iterator from 1)).map {
        case (a, b) ⇒ s"$a-$b"
      }.via(Journal(_ ⇒ Set("foo"))).runWith(Sink.ignore).futureValue

    // note that the flow only gets the 'EventEnvelope' and not the 'offset -> EventEnvelope' pair.
    withQueryFromOffset("q1") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q1").futureValue shouldBe 2)

    withQueryFromOffset("q1") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q1").futureValue shouldBe 4)

    withQueryFromOffset("q1") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q1").futureValue shouldBe 6)
  }

  "multiple queries" should "resume from the last offset" in {
    Source.repeat("foo").take(10)
      .zip(Source.fromIterator(() ⇒ Iterator from 1)).map {
        case (a, b) ⇒ s"$a-$b"
      }.via(Journal(_ ⇒ Set("foo"))).runWith(Sink.ignore).futureValue

    // q1 to 2
    withQueryFromOffset("q1") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q1").futureValue shouldBe 2)

    // q2 to 2
    withQueryFromOffset("q2") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q2").futureValue shouldBe 2)

    // q1 to 4
    withQueryFromOffset("q1") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q1").futureValue shouldBe 4)

    // q1 to 6
    withQueryFromOffset("q1") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q1").futureValue shouldBe 6)

    // q2 to 4
    withQueryFromOffset("q2") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q2").futureValue shouldBe 4)

    // q2 to 6
    withQueryFromOffset("q2") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q2").futureValue shouldBe 6)

    // q1 to 8
    withQueryFromOffset("q1") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q1").futureValue shouldBe 8)

    // q1 to 10
    withQueryFromOffset("q1") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q1").futureValue shouldBe 10)

    // q2 to 8
    withQueryFromOffset("q2") { flow ⇒
      flow.join(takeTwo).run().futureValue shouldBe Done
    }

    eventually(countEvents("q2").futureValue shouldBe 8)

    // q2 to 10
    withQueryFromOffset("q2") { flow ⇒
      flow.join(takeTwoMapToUnit).run().futureValue shouldBe Done
    }

    eventually(countEvents("q2").futureValue shouldBe 10)
  }
}
