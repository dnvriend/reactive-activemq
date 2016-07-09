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

import akka.Done
import akka.stream.scaladsl.Source

import scala.util.{ Failure, Success }

class FileUtilTest extends TestSpec {
  "check file exists" should "find an existing file" in {
    Source.single(FileUtilsCommand.Exists("src/main/resources/reference.conf"))
      .via(FileUtils.exists).testProbe { tp ⇒
        tp.request(1)
        tp.expectNext(FileExistsResult(Success(Done)))
        tp.expectComplete()
      }
  }

  it should "not find a non-existing file" in {
    Source.single(FileUtilsCommand.Exists("src/main/resources/i-do-not-exist.conf"))
      .via(FileUtils.exists).testProbe { tp ⇒
        tp.request(1)
        tp.expectNextPF { case FileExistsResult(Failure(t)) ⇒ }
        tp.expectComplete()
      }
  }

  "check file exists tagged" should "find an existing file" in {
    val tag: String = randomId
    Source.single((tag, FileUtilsCommand.Exists("src/main/resources/reference.conf")))
      .via(FileUtils.existsTagged).testProbe { tp ⇒
        tp.request(1)
        tp.expectNext((tag, FileExistsResult(Success(Done))))
        tp.expectComplete()
      }
  }
}
