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

package com.github.dnvriend.activemq

/**
 * [type basics](https://twitter.github.io/scala_school/type-basics.html)
 * [Variance by Martin Odersky](https://www.youtube.com/watch?v=pgCD10nu_30)
 *  [Fix `Flow` types typing, @uncheckVariance annotations](https://github.com/akka/akka/issues/15971)
 *  [When is @uncheckedVariance needed in Scala, and why is it used](http://stackoverflow.com/questions/2454281/when-is-uncheckedvariance-needed-in-scala-and-why-is-it-used-in-generictravers)
 *  [Akka Stream v1.0 to v2.x migration guide](http://doc.akka.io/docs/akka-stream-and-http-experimental/2.0.2/scala/migration-guide-1.0-2.x-scala.html)
 *  [Akka Stream v2.0.x to v2.4.x migration guide](http://doc.akka.io/docs/akka/2.4.7/scala/stream/migration-guide-2.0-2.4-scala.html)
 *
 * ==TL;DR==
 * Higher kinded types have a variance
 * C[+T] means covariance; C[T'] is a subclass of C[T]
 * C[-T] means contravariance; C[T] is a subclass of C[T']
 * C[T] means invariant; C[T] and C[T'] are not related
 */
package object stream {
  import scala.concurrent.Promise
  type AckTup[A] = (Promise[Unit], A)

  /**
   * scala.annotation.unchecked.uncheckedVariance
   * An annotation for type arguments for which one wants to suppress variance checking.
   * @uncheckVariance is essentially a kind-cast
   *
   * Why?
   * Mutable collections, where the type parameter should be invariant,
   * Immutable collections: where the type parameter should be covariant
   */
}
