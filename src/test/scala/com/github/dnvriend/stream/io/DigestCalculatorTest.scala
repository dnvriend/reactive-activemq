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

package com.github.dnvriend.stream.io

import akka.NotUsed
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.ByteString
import com.github.dnvriend.stream.TestSpec

class DigestCalculatorTest extends TestSpec {
  def withDigestFromText(msg: String, algorithm: Algorithm = Algorithm.MD5)(f: Source[String, NotUsed] ⇒ Unit): Unit =
    f(Source.single(ByteString(msg)).viaMat(DigestCalculator.hexString(algorithm))(Keep.right))

  def withDigestFromResource(name: String, algorithm: Algorithm = Algorithm.MD5)(f: Source[String, NotUsed] ⇒ Unit): Unit =
    withByteStringSource(name)(src ⇒ f(src.viaMat(DigestCalculator.hexString(algorithm))(Keep.right)))

  // OSX
  // $ md5 -s 'hello world'
  // MD5 ("hello world") = 5eb63bbbe01eeed093cb22bb8f5acdc3
  it should "generate MD5 from 'hello world'" in withDigestFromText("hello world") { src ⇒
    src.runWith(Sink.head).futureValue shouldBe "5eb63bbbe01eeed093cb22bb8f5acdc3"
  }

  // OSX
  // $ md5 persons.xml
  // MD5 (persons.xml) = 4d2ce5189e4e33a6922d75a41afc2c8d
  it should "generate MD5 SUM from small file" in withDigestFromResource("xml/persons.xml") { src ⇒
    src.runWith(Sink.head).futureValue shouldBe "4d2ce5189e4e33a6922d75a41afc2c8d"
  }

  // OSX
  // $ shasum persons.xml
  // bf5f076564e251d5562e69f930882384ba818124  persons.xml
  it should "generate SHA-1 SUM from small file" in withDigestFromResource("xml/persons.xml", Algorithm.`SHA-1`) { src ⇒
    src.runWith(Sink.head).futureValue shouldBe "bf5f076564e251d5562e69f930882384ba818124"
  }

  // OSX
  // $ shasum -a 256 persons.xml
  // 0f240ca7391aeacc14aff0feda3a75c4114ac948c965a791d120d0e0e24cf349  persons.xml
  it should "generate SHA-256 SUM from small file" in withDigestFromResource("xml/persons.xml", Algorithm.`SHA-256`) { src ⇒
    src.runWith(Sink.head).futureValue shouldBe "0f240ca7391aeacc14aff0feda3a75c4114ac948c965a791d120d0e0e24cf349"
  }

  // OSX
  // $ md5 lot-of-persons.xml
  // MD5 (lot-of-persons.xml) = 495d085534cb60135a037dc745e8c20f
  it should "generate MD5 from large file" in withDigestFromResource("xml/lot-of-persons.xml") { src ⇒
    src.runWith(Sink.head).futureValue shouldBe "495d085534cb60135a037dc745e8c20f"
  }

  // OSX
  // shasum lot-of-persons.xml
  // 03c295ab3111d577a7bbc41f89c8430203c36a77  lot-of-persons.xml
  it should "generate SHA-1 SUM from large file" in withDigestFromResource("xml/lot-of-persons.xml", Algorithm.`SHA-1`) { src ⇒
    src.runWith(Sink.head).futureValue shouldBe "03c295ab3111d577a7bbc41f89c8430203c36a77"
  }

  // OSX
  // shasum -a 256 lot-of-persons.xml
  // 8cf518ae01861ade9dc0b11d1fbec36cf7a068cb23bd1cb9f4dce0109d4ef9a4  lot-of-persons.xml
  it should "generate SHA-256 SUM from large file" in withDigestFromResource("xml/lot-of-persons.xml", Algorithm.`SHA-256`) { src ⇒
    src.runWith(Sink.head).futureValue shouldBe "8cf518ae01861ade9dc0b11d1fbec36cf7a068cb23bd1cb9f4dce0109d4ef9a4"
  }

  // OSX
  // shasum -a 384 lot-of-persons.xml
  // af87bf364e379bbe73f69b91fadfbf68fc96d0c53ba92b8e0eb104dc2afe200bbc6570d823900358f44f01112d7d5dc7  lot-of-persons.xml
  it should "generate SHA-384 SUM from large file" in withDigestFromResource("xml/lot-of-persons.xml", Algorithm.`SHA-384`) { src ⇒
    src.runWith(Sink.head).futureValue shouldBe "af87bf364e379bbe73f69b91fadfbf68fc96d0c53ba92b8e0eb104dc2afe200bbc6570d823900358f44f01112d7d5dc7"
  }

  // OSX
  // shasum -a 512 lot-of-persons.xml
  // ee8a6a2ca9bfdc386a555b391566c3edfcbd5b68bf9d54108b236b40b09a4106a2728e4f5666077f017c5d894919525e570779926d06814d47f6b31b525e80a6  lot-of-persons.xml
  it should "generate SHA-512 SUM from large file" in withDigestFromResource("xml/lot-of-persons.xml", Algorithm.`SHA-512`) { src ⇒
    src.runWith(Sink.head).futureValue shouldBe "ee8a6a2ca9bfdc386a555b391566c3edfcbd5b68bf9d54108b236b40b09a4106a2728e4f5666077f017c5d894919525e570779926d06814d47f6b31b525e80a6"
  }
}
