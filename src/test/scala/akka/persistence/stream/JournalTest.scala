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
import akka.stream.scaladsl.{ Sink, Source }

class JournalTest extends TestSpec {
  "flow via persistent actor" should "Write 100 messages to the journal" in {
    Source.repeat("foo").take(100).via(Journal.flow(_ => Set("foo"))).runWith(Sink.ignore).futureValue
    journal.currentEventsByTag("foo", 0).runFold(0L) { case (c, e) => c + 1 }.futureValue shouldBe 100
  }

  "flow via direct journal" should "Write messages to the journal" in {
    Source.repeat("foo").take(100).via(Journal.flowDirect(_ => Set("foo"))).runWith(Sink.ignore).futureValue
    journal.currentEventsByTag("foo", 0).runFold(0L) { case (c, e) => c + 1 }.futureValue shouldBe 100
  }
}
