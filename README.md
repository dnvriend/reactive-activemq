# reactive-activemq v0.0.2
An akka-streams __lineair-flow only__ compatible `ActiveMqSource` and `ActiveMqSink` that can consume messages from an ActiveMq `queue` and 
produce messages to an ActiveMq `topic` leveraging backpressure aware lineair flow and ActiveMq VirtualTopics. This project is 
very much work in progress.

This project has been inspired by [op-rabbit][op-rabbit] by [SpinGo][spingo].

Service | Status | Description
------- | ------ | -----------
License | [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) | Apache 2.0
Bintray | [![Download](https://api.bintray.com/packages/dnvriend/maven/reactive-activemq/images/download.svg)](https://bintray.com/dnvriend/maven/reactive-activemq/_latestVersion) | Latest Version on Bintray
Gitter | [![Join the chat at https://gitter.im/dnvriend/reactive-activemq](https://badges.gitter.im/dnvriend/reactive-activemq.svg)](https://gitter.im/dnvriend/reactive-activemq?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) | Chat on Gitter

## Installation
Add the following to your `build.sbt`:

```scala
resolvers += Resolver.jcenterRepo

libraryDependencies += "com.github.dnvriend" %% "reactive-activemq" % "0.0.2"
```

## Limitations
- It is very new,
- Implementation is very sketchy,
- Very limited number of combinators (but enough for my use case),
- Ony supports simple lineair flows,
- Only supports a small number of convenience combinators:
  - `fmap`: the map operation, but exposes only the payload, does not ack the message,
  - `fmapAck`: the map operation, but exposes only the payload, it acks or fails the message depending on the result of the `A => B` function,
  - `fmapAsync`: the async map operation, but exposes only the payload, it acks or fails the message depending on the result of the `A => B` function,
  - `runForeachAck`: the runForeach operation, it acks or fails the message depending on the result of the `A => Unit` function, 

## Why use it?
Good question! The project is very new, so only use it when you really like the akka-stream API. 
I use it to combine consuming messages from ActiveMq with akka-persistence and/or the akka-persistence-query API to ActiveMq,
and nothing beats reading a simple lineair flow using akka-stream!

## Todo:
- Testing,
- Better implementation ??,
- More combinators?? I only need fmap and fmapAsync, filters, collect etc only introduce more problems ie. removing filtered messages from the broker,
- Custom Source/Sink so that the standard non-acking stages cannot be used ?? just like [op-rabbit][op-rabbit],

## Consuming from a queue
To consume from a queue: 

```scala
import akka.Done
import akka.actor.ActorSystem
import akka.camel.CamelMessage
import akka.stream.{ ActorMaterializer, Materializer }
import com.github.dnvriend.activemq.stream.{ ActiveMqSource, MessageExtractor }
import com.github.dnvriend.activemq.stream.AckedFlowOps._

import scala.concurrent.ExecutionContext

case class Foo(txt: String)

object Consumer extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  sys.addShutdownHook(system.terminate())

  implicit val FooExtractor = new MessageExtractor[CamelMessage, Foo] {
    override def extract(in: CamelMessage): Foo = {
      Foo(in.body.asInstanceOf[String])
    }
  }
  println("Launching consumers")
  val f: Future[Done] = ActiveMqSource("consumer1").fmap(foo ⇒ foo.copy(txt = foo.txt + "c1!")).runForeachAck(txt ⇒ println(txt + "c1"))
  val f2: Future[Done] = ActiveMqSource("consumer2").fmap(foo ⇒ foo.copy(txt = foo.txt + "c2!")).runForeachAck(txt ⇒ println(txt + "c2"))
}
```

## Producing to a VirtualTopic
To produce to a topic:

```scala
import akka.Done
import akka.stream.scaladsl.Source
import spray.json.DefaultJsonProtocol._
import JsonMessageBuilder._
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import com.github.dnvriend.activemq.stream.{ ActiveMqSink, MessageExtractor }

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()
implicit val ec: ExecutionContext = system.dispatcher
sys.addShutdownHook(system.terminate())

val f: Future[Done] = Source.fromIterator(() ⇒ Iterator from 0).take(100).map(nr ⇒ List.fill(10)(nr)).runWith(ActiveMqSink("producer1"))
```

## Example configuraton
The configuration is based upon a free to use producer/consumer name, that will point to an ActiveMq connection that will be created
by the `ActiveMqExtension` when it creates the list of connections from the `reactive-activemq.connections` config. 

The connections name must be the name of the connection configuration eg. `amq1`, will point to the `amq1` configuration which must
contain the host, port, user and password fields. 

Consumers are `ActiveMqSource` components and are created using a consumer name. This consumer name must point to a configuration
for example, `consumer1` (bad idea to use this name) and will use a connection, use a queue name (using VirtualTopic semantics) and 
will use a number of concurrent connections. The endpointUri that the `ActiveMqSource` will use will become:

```
amq1:queue:Consumer.consumer1.VirtualTopic.test?concurrentConsumers8"
```

Producers are `ActiveMqSink` components and are created using a producer name. This producer must point to a configuration for example,
`producer1` (bad idea to use this name) and will use a connection and a topic name using VirtualTopic semantics. The endpointUri that the
`ActiveMqSink` will use will become:

```
amq1:topic:VirtualTopic.test"
```

Example config:
```
reactive-activemq {
  connections = ["amq1", "amq2"]
}

amq1 {
  host = "boot2docker"
  port = "61616"
  user = "amq"
  pass = "amq"
}

amq2 {
  host = "boot2docker"
  port = "61616"
  user = "amq"
  pass = "amq"
}

consumer1 {
  conn = "amq1"
  queue = "test"
  concurrentConsumers = "8"
}

consumer2 {
  conn = "amq2"
  queue = "test"
  concurrentConsumers = "8"
}

producer1 {
  conn = "amq2"
  topic = "test"
}
```

## Architecture
The plugin is designed around the following choices:
- Each queue will contain only one message type, we will call this type `T`,
- Consumers will consume from a queue using VirtualTopic semantics: `activemq:queue:Consumer.ConsumerName.VirtualTopic.TopicName?concurrentConsumers=1`,
- Consumers will be called `ActiveMqSource`,
- Consumers need a `MessageExtractor`typeclass to extract messages to type `T`
- Producers will produce to a topic using VirtualTopic semantics: `activemq:topic:VirtualTopic.TopicName`,
- Producers will be called `ActiveMqSink`,
- Procuers need a `MessageBuilder` to create messages to send to a topic,
- Consumers and producers have names that refer to a configured component using Typesafe Config,
- Messages will be consumed using an `ActiveMqSource("consumerName")` and needs an implicit `MessageExtractor`,
- Messages will be produced using an `ActiveMqSink("producerName")` and needs an implicit `MessageBuilder`,

## Extracting Messages
- To extract the message, the typeclass pattern will be used which is a `MessageExtractor[IN, OUT]`,
- `IN` will be defined as a `CamelMessage`
- `OUT` will be defined as a `MessageContext[T, Map[String, Any]]`
- MessageExtractor is therefor defined as: `MessageExtractor[CamelMessage, MessageContext[T, Map[String, Any]]]`
- The MessageExtractor is responsible for extracting the type T *and* extracting any relevant headers
- Having the MessageExtractor pluggable decouples the serialization method 
 
# Acknowledgement in streams
Akka streams only handles backpressure, *not* acknowledgements. Inspired by opt-rabbit, I have tried using the same approach
leveraging akka-streams and akka-camel, using a transactional connection with ActiveMq, acking the messages when needed and failing
when appropiate. 

To complete the transaction between ActiveMq and the stream stage, we need to decide when it is okay to ack a message, and thereby
removing the message from the broker. The following is a list of possible places where we can ack (remove message from the broker) 
or fail (leaving the message on the broker) a message: 

- The source element was processed by the sink,
- The element was eliminated by a `filter` or `collect`,
- In the case many elements were grouped together via `grouped`, `groupedWithin`, etc., the resulting group completed,

Error signaling is important, too. If an element fails in any give stage of the stream, then the error should be propagated 
up through the `acknowledgement channel`.
 
## Is acknowledgement needed?
Without acknowledgement, then there is no hope for retrying messages that failed because of simple chaos. If the process crashes, 
or the stream is not brought down gracefully, then the messages in-flight are lost. In most cases, this is probably acceptable. 
But, in others, it's not.
 
## How to implement?
Akka streams is leveraging the `backpressure channel` to leverage well, backpressure from every component in the flow up to the source. This 
channel is based on the need on having backpressure and all components coming out of the box from akka-stream implement this channel. For 
our use case however we have a need for `acknowledgement` so we need an `acknowledgement channel` and as such we must create new stream 
component that support this new `acknowledgement channel`. For starters, we need an `AckedSource`, and `AckedFlow` and an `AckedSink` for
all components to be able to acknowledge messages from the `Sink` up to the `Source`.  

## Resources
- [The Need for Acknowledgement in Streams][need-for-ack]
- [op-rabbit][op-rabbit]

# Whats new?
- v0.0.3 (2016-06-29)
  - To initialize connections, a list of connections is added to `reactive-activemq` settings which will be
    initialized as soon as possible as they will be created by the ActiveMqExtension,
  - Each consumer/producer has its own (free to choose) name and is read from configuration.
- v0.0.2 (2016-06-28)
  - Cleanup some of the code.
- v0.0.1 (2016-06-28)
  - Initial release.

[need-for-ack]: http://tim.theenchanter.com/2015/07/the-need-for-acknowledgement-in-streams.html
[op-rabbit]: https://github.com/SpinGo/op-rabbit
[spingo]: https://www.spingo.com/