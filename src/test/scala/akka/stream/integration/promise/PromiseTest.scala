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

import scala.concurrent.{ Future, Promise }

class PromiseTest extends TestSpec {

  def withPromise[A]()(fn: (Promise[A], Future[A]) => Unit): Unit = {
    val p: Promise[A] = Promise[A]()
    val f: Future[A] = p.future
    fn(p, f)
  }

  "a promise" should "be completed successfully" in withPromise[Int]() { (p, f) =>
    p success 1
    p shouldBe 'completed
    f.futureValue shouldBe 1
    p shouldBe 'completed
  }

  it should "not be completed multiple times" in withPromise[Int]() { (p, f) =>
    p success 1
    p shouldBe 'completed
    intercept[IllegalStateException] {
      p success 2
    }
  }

  it should "not be completed with a success and then with a failure" in withPromise[Int]() { (p, f) =>
    p failure new RuntimeException("Test failure")
    p shouldBe 'completed
    f.toTry should be a 'failure
    intercept[IllegalStateException] {
      p success 2
    }
  }

  it should "be completed with a failure" in withPromise[Int]() { (p, f) =>
    p failure new RuntimeException("Test failure")
    p shouldBe 'completed
    f.toTry should be a 'failure
  }

  it should "not be completed with a failure and then with a success" in withPromise[Int]() { (p, f) =>
    p failure new RuntimeException("Test failure")
    p shouldBe 'completed
    intercept[IllegalStateException] {
      p success 2
    }
  }
}
