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

package akka.stream.integration.activemq.extension

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.camel.CamelExtension
import akka.stream.integration.activemq.extension.config.{ ActiveMqConfig, ConsumerConfig, ProducerConfig }
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.camel.component.ActiveMQComponent
import org.apache.camel.component.jms.JmsConfiguration

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

  system.settings.config.getStringList("reactive-activemq.connections").foreach { componentName =>
    val amqConfig = ActiveMqConfig(system.settings.config.getConfig(componentName))
    createComponent(componentName, amqConfig)
  }

  private def createComponent(componentName: String, amqConfig: ActiveMqConfig): Unit = {
    val connectionFactory = new ActiveMQConnectionFactory(amqConfig.user, amqConfig.pass, s"${amqConfig.transport}://${amqConfig.host}:${amqConfig.port}")
    val jmsConfiguration: JmsConfiguration = new JmsConfiguration()
    jmsConfiguration.setConnectionFactory(connectionFactory)
    val ctx = CamelExtension(system).context
    val component = ctx.getComponent("activemq").asInstanceOf[ActiveMQComponent]
    component.setConfiguration(jmsConfiguration)
    component.setTransacted(true)
    ctx.addComponent(componentName, component)
  }

  override def consumerEndpointUri(consumerName: String): String =
    ConsumerConfig(system.settings.config.getConfig(consumerName), consumerName).endpoint

  override def producerEndpointUri(producerName: String): String =
    ProducerConfig(system.settings.config.getConfig(producerName)).endpoint
}