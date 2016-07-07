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

package com.github.dnvriend.stream.activemq.extension

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.camel.CamelExtension
import com.typesafe.config.Config
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.camel.component.ActiveMQComponent
import org.apache.camel.component.jms.JmsConfiguration
import scalaz.syntax.std.boolean._

case class ActiveMqConfig(host: String, port: String, user: String, pass: String)

case class ConsumerConfig(conn: String, queue: String, concurrentConsumers: String)

case class ProducerConfig(conn: String, topic: String, replyTo: Option[String])

object ActiveMqExtension extends ExtensionId[ActiveMqExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ActiveMqExtensionImpl = new ActiveMqExtensionImpl(system)

  override def lookup(): ExtensionId[_ <: Extension] = ActiveMqExtension
}

trait ActiveMqExtension {
  def consumerEndpointUri(consumerName: String): String
  def producerEndpointUri(producerName: String): String
}

class ActiveMqExtensionImpl(val system: ExtendedActorSystem) extends Extension with ActiveMqExtension {
  import scala.collection.JavaConversions._
  system.settings.config.getStringList("reactive-activemq.connections").foreach { componentName ⇒
    val amqConfig = activeMqConfig(system.settings.config.getConfig(componentName))
    createComponent(componentName, amqConfig)
  }

  private def activeMqConfig(config: Config) = ActiveMqConfig(
    config.getString("host"),
    config.getString("port"),
    config.getString("user"),
    config.getString("pass")
  )

  private def createComponent(componentName: String, amqConfig: ActiveMqConfig): Unit = {
    val connectionFactory = new ActiveMQConnectionFactory(amqConfig.user, amqConfig.pass, s"nio://${amqConfig.host}:${amqConfig.port}")
    val jmsConfiguration: JmsConfiguration = new JmsConfiguration()
    jmsConfiguration.setConnectionFactory(connectionFactory)
    val ctx = CamelExtension(system).context
    val component = ctx.getComponent("activemq").asInstanceOf[ActiveMQComponent]
    component.setConfiguration(jmsConfiguration)
    component.setTransacted(true)
    ctx.addComponent(componentName, component)
  }

  private def consumerConfig(config: Config) = ConsumerConfig(
    config.getString("conn"),
    config.getString("queue"),
    config.getString("concurrentConsumers")
  )

  private def producerConfig(config: Config) = ProducerConfig(
    config.getString("conn"),
    config.getString("topic"),
    config.hasPath("reply-to").option(config.getString("reply-to"))
  )

  override def consumerEndpointUri(consumerName: String): String = {
    val cfg = consumerConfig(system.settings.config.getConfig(consumerName))
    import cfg._
    val destination = s"$conn:queue:Consumer.$consumerName.VirtualTopic.$queue?concurrentConsumers=$concurrentConsumers"
    destination
  }

  override def producerEndpointUri(producerName: String): String = {
    val cfg = producerConfig(system.settings.config.getConfig(producerName))
    import cfg._
    val maybeReplyTo = replyTo.map(dest ⇒ s"?replyTo=$dest&preserveMessageQos=true").getOrElse("")
    s"$conn:topic:VirtualTopic.$topic$maybeReplyTo"
  }
}