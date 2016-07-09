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

///*
// * Copyright 2016 Dennis Vriend
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package akka.stream.integration
//
//import java.io.File
//
//import org.apache.activemq.broker.BrokerService
//import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
//
//trait EmbeddedBroker extends BeforeAndAfterAll with BeforeAndAfterEach { _: TestSpec ⇒
//  var broker: BrokerService = _
//
//  override protected def beforeAll(): Unit = {
//    super.beforeAll()
//  }
//
//  override protected def beforeEach(): Unit = {
//    val id = randomId
//    val dataDir = s"target/activemq-$id"
//    new File(dataDir).mkdir()
//    broker = new BrokerService()
//    broker.addConnector("tcp://localhost:61616")
//    broker.setDataDirectory(dataDir)
//    broker.start()
//    broker.waitUntilStarted()
//    super.beforeEach()
//  }
//
//  def stopAmq: Unit = Option(broker).foreach { broker ⇒
//    broker.stop()
//    broker.waitUntilStopped()
//  }
//
//  override protected def afterEach(): Unit = {
//    stopAmq
//    super.afterEach()
//  }
//
//  override protected def afterAll(): Unit = {
//    stopAmq
//    super.afterAll()
//  }
//}
