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

package akka.stream.integration.activemq.extension.config

import com.typesafe.config.Config

import scalaz.syntax.std.boolean._

object ActiveMqConfig {
  def apply(config: Config): ActiveMqConfig = {
    val url = config.hasPath("url").option(config.getString("url")).getOrElse {
      val host = config.getString("host")
      val port = config.getString("port")
      val transport = config.hasPath("transport").option(config.getString("transport")).getOrElse("nio")
      s"$transport://$host:$port"
    }
    ActiveMqConfig(url, config.getString("user"), config.getString("pass"))
  }
}

case class ActiveMqConfig(url: String, user: String, pass: String)
