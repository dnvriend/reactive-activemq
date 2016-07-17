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
package io

import akka.NotUsed
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future

class DigestCalculatorTest extends TestSpec {
  def withDigestFromText(msg: String, algorithm: Algorithm = Algorithm.MD5)(f: Source[String, NotUsed] => Unit): Unit =
    f(Source.single(ByteString(msg)).viaMat(DigestCalculator.hexString(algorithm))(Keep.right))

  def withDigestFromResource(name: String, algorithm: Algorithm = Algorithm.MD5)(f: Source[String, NotUsed] => Unit): Unit =
    withByteStringSource(name)(src => f(src.viaMat(DigestCalculator.hexString(algorithm))(Keep.right)))

  // OSX
  // $ md5 -s 'hello world'
  // MD5 ("hello world") = 5eb63bbbe01eeed093cb22bb8f5acdc3
  it should "generate MD5 from 'hello world'" in withDigestFromText("hello world") { src =>
    src.runWith(Sink.head).futureValue shouldBe "5eb63bbbe01eeed093cb22bb8f5acdc3"
  }

  // OSX
  // $ md5 persons.xml
  // MD5 (persons.xml) = 857dd889c523c02251f13839e449bc56
  it should "generate MD5 SUM from small file" in withDigestFromResource("xml/persons.xml") { src =>
    src.runWith(Sink.head).futureValue shouldBe "857dd889c523c02251f13839e449bc56"
  }

  // OSX
  // $ shasum persons.xml
  // 134dfc1f45bdaf20f3eb0840fcd267a6448f5dec  persons.xml
  it should "generate SHA-1 SUM from small file" in withDigestFromResource("xml/persons.xml", Algorithm.`SHA-1`) { src =>
    src.runWith(Sink.head).futureValue shouldBe "134dfc1f45bdaf20f3eb0840fcd267a6448f5dec"
  }

  // OSX
  // $ shasum -a 256 persons.xml
  // a04cafbdd92ee705b9c1c70051a58d8629bfcf7031a8d2bf767547adaa63e586  persons.xml
  it should "generate SHA-256 SUM from small file" in withDigestFromResource("xml/persons.xml", Algorithm.`SHA-256`) { src =>
    src.runWith(Sink.head).futureValue shouldBe "a04cafbdd92ee705b9c1c70051a58d8629bfcf7031a8d2bf767547adaa63e586"
  }

  // OSX
  // $ md5 lot-of-persons.xml
  // MD5 (lot-of-persons.xml) = 495d085534cb60135a037dc745e8c20f
  it should "generate MD5 from large file" in withDigestFromResource("xml/lot-of-persons.xml") { src =>
    src.runWith(Sink.head).futureValue shouldBe "495d085534cb60135a037dc745e8c20f"
  }

  // OSX
  // shasum lot-of-persons.xml
  // 03c295ab3111d577a7bbc41f89c8430203c36a77  lot-of-persons.xml
  it should "generate SHA-1 SUM from large file" in withDigestFromResource("xml/lot-of-persons.xml", Algorithm.`SHA-1`) { src =>
    src.runWith(Sink.head).futureValue shouldBe "03c295ab3111d577a7bbc41f89c8430203c36a77"
  }

  // OSX
  // shasum -a 256 lot-of-persons.xml
  // 8cf518ae01861ade9dc0b11d1fbec36cf7a068cb23bd1cb9f4dce0109d4ef9a4  lot-of-persons.xml
  it should "generate SHA-256 SUM from large file" in withDigestFromResource("xml/lot-of-persons.xml", Algorithm.`SHA-256`) { src =>
    src.runWith(Sink.head).futureValue shouldBe "8cf518ae01861ade9dc0b11d1fbec36cf7a068cb23bd1cb9f4dce0109d4ef9a4"
  }

  // OSX
  // shasum -a 384 lot-of-persons.xml
  // af87bf364e379bbe73f69b91fadfbf68fc96d0c53ba92b8e0eb104dc2afe200bbc6570d823900358f44f01112d7d5dc7  lot-of-persons.xml
  it should "generate SHA-384 SUM from large file" in withDigestFromResource("xml/lot-of-persons.xml", Algorithm.`SHA-384`) { src =>
    src.runWith(Sink.head).futureValue shouldBe "af87bf364e379bbe73f69b91fadfbf68fc96d0c53ba92b8e0eb104dc2afe200bbc6570d823900358f44f01112d7d5dc7"
  }

  // OSX
  // shasum -a 512 lot-of-persons.xml
  // ee8a6a2ca9bfdc386a555b391566c3edfcbd5b68bf9d54108b236b40b09a4106a2728e4f5666077f017c5d894919525e570779926d06814d47f6b31b525e80a6  lot-of-persons.xml
  it should "generate SHA-512 SUM from large file" in withDigestFromResource("xml/lot-of-persons.xml", Algorithm.`SHA-512`) { src =>
    src.runWith(Sink.head).futureValue shouldBe "ee8a6a2ca9bfdc386a555b391566c3edfcbd5b68bf9d54108b236b40b09a4106a2728e4f5666077f017c5d894919525e570779926d06814d47f6b31b525e80a6"
  }

  it should "generate multiple hashes from text" in {
    def hashForAlgorithm(algorithm: Algorithm): Future[Seq[String]] =
      Source(List("foo", "bar")).flatMapConcat(txt => DigestCalculator.source(algorithm, txt)).runWith(Sink.seq)

    hashForAlgorithm(Algorithm.MD5).futureValue shouldBe List("acbd18db4cc2f85cedef654fccc4a4d8", "37b51d194a7513e45b56f6524f2d51f2")
    hashForAlgorithm(Algorithm.`SHA-1`).futureValue shouldBe List("0beec7b5ea3f0fdbc95d0dd47f3c5bc275da8a33", "62cdb7020ff920e5aa642c3d4066950dd1f01f4d")
    hashForAlgorithm(Algorithm.`SHA-256`).futureValue shouldBe List("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae", "fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9")
    hashForAlgorithm(Algorithm.`SHA-512`).futureValue shouldBe List("f7fbba6e0636f890e56fbbf3283e524c6fa3204ae298382d624741d0dc6638326e282c41be5e4254d8820772c5518a2c5a8c0c7f7eda19594a7eb539453e1ed7", "d82c4eb5261cb9c8aa9855edd67d1bd10482f41529858d925094d173fa662aa91ff39bc5b188615273484021dfb16fd8284cf684ccf0fc795be3aa2fc1e6c181")
  }
}
