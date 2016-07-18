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
package promise

import scala.collection.immutable.Seq
import scala.concurrent.{ Future, Promise }
import scala.util.Try

class SeqPromisesTest extends TestSpec {
  def withPromise[T, U](complete: Try[T] => U, failure: PartialFunction[Throwable, U]): (Promise[T], Future[T]) = {
    val p = Promise[T]()
    val f = p.future
    f.onComplete(complete)
    f.onFailure(failure)
    p -> f
  }

  def withPromises()(f: Seq[(Promise[Unit], Future[Unit])] => Unit): Unit = f(Seq(
    withPromise((_: Try[Unit]) => (), PartialFunction.empty),
    withPromise((_: Try[Unit]) => (), PartialFunction.empty),
    withPromise((_: Try[Unit]) => (), PartialFunction.empty)
  ))

  it should "complete a promise" in withPromises() { xs =>
    xs.head._1.success(())
    xs.head._2.futureValue shouldBe ()
    xs.filterNot(_._1.isCompleted).size shouldBe 2
  }

  it should "complete multiple promises" in withPromises() { xs =>
    xs.zipWithIndex.foreach {
      case ((p, f), 0) =>
        p success (); f.futureValue shouldBe ()
      case ((p, f), 1) =>
        p success (); f.futureValue shouldBe ()
      case ((p, f), 2) =>
        p success (); f.futureValue shouldBe ()
    }
  }
}
