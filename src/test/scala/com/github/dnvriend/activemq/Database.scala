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

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import slick.jdbc.JdbcBackend

object Database extends ExtensionId[DatabaseImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): DatabaseImpl = new DatabaseImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = Database
}

class DatabaseImpl()(implicit val system: ExtendedActorSystem) extends JdbcBackend with Extension {
  val db: Database = Database.forConfig("slick.db", system.settings.config)
}
