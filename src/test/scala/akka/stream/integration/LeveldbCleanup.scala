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

import java.io.File

import akka.actor.PoisonPill
import akka.testkit.TestProbe
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll

import scala.util.Try

trait LeveldbCleanup extends BeforeAndAfterAll { _: TestSpec ⇒
  lazy val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.snapshot-store.local.dir"
  ).map(s ⇒ new File(system.settings.config.getString(s)))

  override protected def beforeAll() {
    super.beforeAll()
    Try(storageLocations.foreach(FileUtils.deleteDirectory))
  }

  override protected def afterAll() {
    super.afterAll()
    Try(storageLocations.foreach(FileUtils.deleteDirectory))
    //    Try(storageLocations.foreach(FileUtils.forceMkdir))
  }
}
