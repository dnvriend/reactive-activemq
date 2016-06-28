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

package com.github.dnvriend.activemq.extension

import akka.actor.{ Actor, ActorLogging, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props }
import akka.camel.CamelExtension
import akka.util.Timeout
import com.github.dnvriend.activemq.extension.Cache.{ ConsumerFor, ProducerFor }
import com.typesafe.config.Config
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.camel.component.ActiveMQComponent
import org.apache.camel.component.jms.JmsConfiguration

import scala.concurrent.{ Await, Future }

case class ActiveMqConfig(name: String, host: String, port: String, user: String, pass: String)

case class ConsumerConfig(consumerName: String, queue: String)

case class ProducerConfig(topic: String)

object ActiveMqExtension extends ExtensionId[ActiveMqExtensionImpl] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): ActiveMqExtensionImpl = new ActiveMqExtensionImpl(system)

  override def lookup(): ExtensionId[_ <: Extension] = ActiveMqExtension
}

trait ActiveMqExtension {
  def consumerFor(name: String): String

  def producerFor(name: String): Future[String]
}

object Cache {
  case class ConsumerFor(name: String)
  case class ProducerFor(name: String)
}
class Cache extends Actor with ActorLogging {
  def activeMqConfig(config: Config) = ActiveMqConfig(
    config.getString("conn.name"),
    config.getString("conn.host"),
    config.getString("conn.port"),
    config.getString("conn.user"),
    config.getString("conn.pass")
  )

  def consumerConfig(config: Config) = ConsumerConfig(
    config.getString("name"),
    config.getString("queue")
  )

  def producerConfig(config: Config) = ProducerConfig(config.getString("topic"))

  def createComponent(amqConfig: ActiveMqConfig): Unit = {
    log.debug("Creating component: {}", amqConfig)
    val connectionFactory = new ActiveMQConnectionFactory(amqConfig.user, amqConfig.pass, s"nio://${amqConfig.host}:${amqConfig.port}")
    val jmsConfiguration: JmsConfiguration = new JmsConfiguration()
    jmsConfiguration.setConnectionFactory(connectionFactory)
    val ctx = CamelExtension(context.system).context
    val component = ctx.getComponent("activemq").asInstanceOf[ActiveMQComponent]
    component.setConfiguration(jmsConfiguration)
    component.setTransacted(true)
    ctx.addComponent(amqConfig.name, component)
  }

  override def receive: Receive = cache(Vector.empty)

  def cache(xs: Vector[String]): Receive = {
    case ConsumerFor(name) ⇒
      val cfg = consumerConfig(context.system.settings.config.getConfig(name))
      val amqConfig = activeMqConfig(context.system.settings.config.getConfig(name))
      if (!xs.contains(amqConfig.name)) {
        context.become(cache(xs :+ amqConfig.name))
        createComponent(amqConfig)
      }
      sender() ! s"${amqConfig.name}:queue:Consumer.${cfg.consumerName}.VirtualTopic.${cfg.queue}?concurrentConsumers=1"

    case ProducerFor(name) ⇒
      val cfg = producerConfig(context.system.settings.config.getConfig(name))
      val amqConfig = activeMqConfig(context.system.settings.config.getConfig(name))
      if (!xs.contains(name)) {
        context.become(cache(xs :+ name))
        createComponent(amqConfig)
      }
      sender() ! s"${amqConfig.name}:topic:VirtualTopic.${cfg.topic}"
  }
}

class ActiveMqExtensionImpl(val system: ExtendedActorSystem) extends Extension with ActiveMqExtension {
  import akka.pattern.ask

  import scala.concurrent.duration._
  val cache = system.actorOf(Props[Cache])
  implicit val timeout = Timeout(10.seconds)

  def consumerFor(name: String): String =
    Await.result((cache ? ConsumerFor(name)).mapTo[String], 10.seconds)

  def producerFor(name: String): Future[String] =
    (cache ? ProducerFor(name)).mapTo[String]
}