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

import java.io.InputStream
import java.net.URL

import akka.stream.integration.BrokerResources.{ QueueStat, TopicStat }
import org.scalatest.BeforeAndAfterEach

import scala.xml.NodeSeq

trait BrokerResources extends BeforeAndAfterEach { _: TestSpec ⇒
  private def callBroker(path: String): InputStream = {
    val url = new URL("http://boot2docker:8161" + path)
    val urlConnection = url.openConnection()
    val basicAuth = "Basic " + new String(java.util.Base64.getUrlEncoder.encode("admin:admin".getBytes()))
    urlConnection.addRequestProperty("Authorization", basicAuth)
    urlConnection.getInputStream
  }

  // communicate with the broker //
  private def getQueueXmlFromBroker: NodeSeq = {
    import scala.xml.XML
    XML.load(callBroker("/admin/xml/queues.jsp"))
  }

  def getTopicXmlFromBroker: NodeSeq = {
    import scala.xml.XML
    XML.load(callBroker("/admin/xml/topics.jsp"))
  }

  def getQueueStats: List[QueueStat] = (for {
    e ← getQueueXmlFromBroker \\ "queue"
    stat ← e \ "stats"
  } yield QueueStat(
    (e \ "@name").text,
    (stat \ "@size").text.toInt,
    (stat \ "@consumerCount").text.toInt,
    (stat \ "@enqueueCount").text.toInt,
    (stat \ "@dequeueCount").text.toInt
  )).toList

  def getTopicStats: List[TopicStat] = (for {
    e ← getTopicXmlFromBroker \\ "topic"
    stat ← e \ "stats"
  } yield TopicStat(
    (e \ "@name").text,
    (stat \ "@size").text.toInt,
    (stat \ "@consumerCount").text.toInt,
    (stat \ "@enqueueCount").text.toInt,
    (stat \ "@dequeueCount").text.toInt
  )).toList

  def purgeQueues(): Unit = {
    def purgeQueue(destinationName: String): InputStream = {
      val path = s"/api/jolokia/exec/org.apache.activemq:brokerName=localhost,destinationName=$destinationName,destinationType=Queue,type=Broker/purge"
      callBroker(path)
    }
    getQueueList.foreach(purgeQueue)
  }

  def getQueueList: List[String] = (for {
    e ← getQueueXmlFromBroker \\ "queue"
  } yield (e \ "@name").text).toList

  def getQueueStatFor(topic: String): Option[QueueStat] =
    getQueueStats.find(_.name contains topic)

  def getQueueMessageCount(topic: String): Option[Int] = for {
    stat ← getQueueStatFor(topic)
  } yield stat.enqueueCount - stat.dequeueCount

  override protected def beforeEach(): Unit = {
    purgeQueues()
  }
}

object BrokerResources {
  case class QueueStat(name: String, size: Int, consumerCount: Int, enqueueCount: Int, dequeueCount: Int)
  case class TopicStat(name: String, size: Int, consumerCount: Int, enqueueCount: Int, dequeueCount: Int)
}
