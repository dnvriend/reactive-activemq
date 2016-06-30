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

package com.github.dnvriend.activemq.stream

import akka.NotUsed
import akka.actor.ActorRef
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.{ Keep, Source }
import akka.testkit.TestProbe
import com.github.dnvriend.activemq.TestSpec

import scala.concurrent.duration._

class ResumableQuerySourceTest extends TestSpec {

  def withQueryFromOffset(f: Source[AckTup[EventEnvelope], ActorRef] ⇒ Unit): Unit =
    f(ResumableQuerySource("NumberJournalQuery", "jdbc-read-journal") { offset ⇒
      val startFrom = offset + 1
      println("Starting from: " + startFrom)
      journal.eventsByPersistenceId("NumberJournal", startFrom, Long.MaxValue)
    })

  it should "resume from the last offset" in {
    Source.fromIterator(() ⇒ Iterator from 0).take(10).runWith(JournalSink("NumberJournal"))
    eventually(countJournal("NumberJournal").futureValue shouldBe 10)

    (0 to 3) foreach { _ ⇒
      withQueryFromOffset { src ⇒
        val (ref, fut) = src.take(2).toMat(AckSink.foreach(println))(Keep.both).run()
        fut.futureValue
        //        Thread.sleep(5.seconds.toMillis)
        val tp = TestProbe()
        tp watch ref
        tp.expectTerminated(ref)
      }
    }
  }
}