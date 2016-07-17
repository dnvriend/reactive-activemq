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

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.stream.integration.TestSpec
import akka.stream.scaladsl.{ Flow, Sink, Source }

import scala.collection.immutable.Seq
import scala.concurrent.Promise

class AckJournalTest extends TestSpec {
  it should "write messages to the journal and ack messages" in withPromises { src => promises =>
    val tags = (x: Any) => Set(x.toString)
    AckJournal(src, tags, Flow[String].map(identity)).toTry should be a 'success
    promises.foreach {
      case (p, _) => p shouldBe 'completed
    }

    journal.currentEventsByTag("foo", 0).runWith(Sink.seq).futureValue should matchPattern {
      case Seq(EventEnvelope(1, _, 1, "foo"), EventEnvelope(2, _, 1, "foo")) =>
    }

    journal.currentEventsByTag("bar", 0).runWith(Sink.seq).futureValue should matchPattern {
      case Seq(EventEnvelope(3, _, 1, "bar"), EventEnvelope(4, _, 1, "bar")) =>
    }
  }

  it should "pre-process messages to strings" in withPromises { src => promises =>
    val tags = (_: Any) => Set("all")
    val preProcess = Flow[String].zipWith(Source.fromIterator(() => Iterator from 0)) {
      case (str, i) => s"str-$i"
    }
    AckJournal(src, tags, preProcess).toTry should be a 'success
    journal.currentEventsByTag("all", 0).runWith(Sink.seq)
      .futureValue.map(_.event) shouldBe Seq("str-0", "str-1", "str-2", "str-3")
  }

  it should "pre-process messages to other types" in withPromises { src => promises =>
    val tags = (_: Any) => Set("all")
    val preProcess = Flow[String].zipWith(Source.fromIterator(() => Iterator from 0)) {
      case (str, i) => i
    }
    AckJournal(src, tags, preProcess).toTry should be a 'success
    journal.currentEventsByTag("all", 0).runWith(Sink.seq)
      .futureValue.map(_.event) shouldBe Seq(0, 1, 2, 3)
  }

  def withPromises(f: Source[(Promise[Unit], String), NotUsed] => Iterable[(Promise[Unit], String)] => Unit): Unit = {
    val promises = (1 to 4).map {
      case i if i <= 2 => (Promise[Unit](), "foo")
      case _           => (Promise[Unit](), "bar")
    }
    f(Source(promises))(promises)
  }
}