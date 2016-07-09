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

import scala.concurrent.Promise

class AckJournalTest extends TestSpec {
  it should "write messages to the journal and ack messages" in {
    val inputPromise1 = Promise[Unit]()
    val inputPromise2 = Promise[Unit]()

    Source(List((inputPromise1, "foo"), (inputPromise2, "bar")))
      .via(AckJournal.flow({ case tag: String ⇒ Set(tag) }))
      .runWith(Sink.ignore).toTry should be a 'success

    inputPromise1 shouldBe 'completed
    inputPromise2 shouldBe 'completed
  }
}
