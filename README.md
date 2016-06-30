# reactive-activemq v0.0.3
reactive-activemq is an [akka-streams][akka-streams] compatible connector for [ActiveMq][amq] providing two 
components, the [ActiveMqSource][amqsource] and [ActiveMqSink][amqsink] that can consume and produce messages with 
[VirtualTopic][vt] semantics, using [akka-streams][akka-streams]'s [demand stream][demand] feature to control the
message flow between components. This project is very much work in progress.

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

libraryDependencies += "com.github.dnvriend" %% "reactive-activemq" % "0.0.3"
```

## Limitations
- It is very new,
- Only supports a small number of convenience combinators on the [ActiveMqSource][amqsource].

## Why use it?
Good question! The project is very new, so only use it when you really like the [akka-streams][akka-streams] API. 
I use it to combine consuming messages from [ActiveMq][amq] with [akka-persistence][akka-persistence] and/or 
[akka-persistence-query][akka-persistence-query] API to [ActiveMq][amq], because nothing beats reading a 
simple [linear flow][linear] using [akka-streams][akka-streams]!

## Todo:
- !! Testing !!,
- ?? Better implementation ??,
- ?? More combinators ??
- ?? Custom Source/Sink so that the standard non-acking stages cannot be used ?? just like [op-rabbit][op-rabbit],

# List of components

## ActiveMqSource
[ActiveMqSource][amqsource] support the following convenience combinators:
- [fmap][fmap]: the map operation, but exposes only the payload, does not ack the message,
- [fmapAck][fmapack]: the map operation, but exposes only the payload, it acks or fails the message depending on the result of the `A => B` function,
- [fmapAsync][fmapasync]: the async map operation, but exposes only the payload, it acks or fails the message depending on the result of the `A => B` function,
- [runForeachAck][runforeach]: the runForeach operation, it acks or fails the message depending on the result of the `A => Unit` function, [materializes][mat] the stream to a `Future[Done]`. 

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

## ActiveMqSink
[ActiveMqSink][amqsink] can be used on any graph as a Sink as long as there is an implicit [MessageBuilder][builder] in scope that 
can convert a `T` into a [CamelMessage][msg]. It materializes into a `Future[Done]`.

## Producing to a VirtualTopic
To produce to a [VirtualTopic][vt]:

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
The [configuration][config] is based upon a free to use producer/consumer name, that will point to an [ActiveMq][amq] connection that 
will be created by the [ActiveMqExtension][extension] when it creates the list of connections from the `reactive-activemq.connections` 
[config][config]. 

The connections name must be the name of the connection [configuration][config] eg. `amq1`, will point to the `amq1` [configuration][config] which must
contain the host, port, user and password fields. 

Consumers are [ActiveMqSource][amqsource] components and are created using a consumer name. This consumer name must point to a [configuration][config]
for example, `consumer1` (bad idea to use this name) and will use a connection, use a queue name (using [VirtualTopic][vt] semantics) and 
will use a number of concurrent connections. The endpointUri that the [ActiveMqSource][amqsource] will use will become:

```
amq1:queue:Consumer.consumer1.VirtualTopic.test?concurrentConsumers=8"
```

Producers are [ActiveMqSink][amqsink] components and are created using a producer name. This producer must point to a [configuration][config] for example,
`producer1` (bad idea to use this name) and will use a connection and a topic name using [VirtualTopic][vt] semantics. The endpointUri that the
[ActiveMqSink][amqsink] will use will become:

```
amq1:topic:VirtualTopic.test"
```

Example [configuration][config]:
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
# JournalSink
...

```scala
```

# AckJournalSink
The `com.github.dnvriend.activemq.stream.AckJournalSink` can be used to store unmarshalled messages received from
a [reactive-activemq][reactive-activemq] `ActiveMqSource` to a journal. When creating reactive applications
having messages in a journal is much more handy than having messages on a queue eg. multiple resumable projection or
resumable queries that can restart from any offset in a journal, but restarting from a queue is very difficult and most
of the time impossible.

The AckJournalSink receives the AckTup[A] that [reactive-activemq][reactive-activemq] uses to ack messages from the queue
when the message has been successfully stored in the journal, or fails the message when the message could not be stored
or serialized to the journal, effectively leaving the message on ActiveMq.

The AckJournalSink can be configured with the following options:
- journalName: the journalName is an alias for the persistenceId. The underlying engine of the AckJournalSink is a
  `PersistentActor` with `ActorSubscriber` which makes it backpressure aware and journal aware.
- journalPluginId: the journalPluginId to use. When left empty, the default journal plugin will be used as configured in
  `application.conf`,
- tags: A function from A => Set[String], makes it possible to match on `A` and tag the message in the journal with a number
  of tokens. This is useful for resumable projects that will use these tags to eg. build views.

Example:
```scala
import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.stream.{ ActorMaterializer, Materializer }
import scala.concurrent.ExecutionContext
import com.github.dnvriend.activemq.stream.{ AckJournalSink, ActiveMqSource, MessageExtractor }

implicit val system: ActorSystem = ActorSystem("importer-draw-result")
implicit val log: LoggingAdapter = Logging(system, this.getClass)
implicit val mat: Materializer = ActorMaterializer()
implicit val ec: ExecutionContext = system.dispatcher
sys.addShutdownHook(system.terminate())

final case class MessageOne(id: String)

implicit val MessageOneMesageExtractor = new MessageExtractor[CamelMessage, MessageOne] {
    override def extract(in: CamelMessage): MessageOne = {
      MessageOne("foo")
    }
}

def tags(msg: MessageOne): Set[String] = msg match {
  case MessageOne(id) ⇒ Set(msg.getClass.getSimpleName)
}

ActiveMqSource("queue1").runWith(AckJournalSink[MessageOne]("MessageReceivedJournal", tags))
```

# Architecture
The plugin is designed around the following choices:
- Each queue will contain only one message type, we will call this type `T`,

## Consumers receive Messages
- Consumers will consume from a queue using [VirtualTopic][vt] semantics: `activemq:queue:Consumer.ConsumerName.VirtualTopic.TopicName?concurrentConsumers=1`,
- Consumers will be called [ActiveMqSource][amqsource],
- Consumers need a [MessageExtractor][extractor]typeclass to extract messages to type `T`
- Consumers have names that refer to a [configured][config] component using [Typesafe Config][typesafe-config],
- Consumers will be implemented using [akka-streams][akka-streams] and are a `ActiveMqSource("consumerName")` and need an implicit [MessageExtractor][extractor] to consume/receive messages,

## Consuming/Receiving and Extracting Messages
The [ActiveMqSource][amqsource] needs an implicit [MessageExtractor][extractor] to convert a [CamelMessage][msg] to a `T`.
 
- To extract the message, the typeclass pattern will be used which is a `MessageExtractor[IN, OUT]`,
- `IN` will be defined as a [CamelMessage][msg],
- `OUT` will be defined as a `T`,
- [MessageExtractor][extractor] is therefor defined as: `MessageExtractor[CamelMessage, T]`,
- The MessageExtractor is responsible for extracting the type T *and* extracting any relevant headers,
- Having the MessageExtractor pluggable decouples the unmarshalling method.

## Producers send Messages
- Producers will produce to a topic using [VirtualTopic][vt] semantics: `activemq:topic:VirtualTopic.TopicName`,
- Producers will be called [ActiveMqSink][amqsink],
- Producers need a `MessageBuilder` to create messages to send to a topic,
- Producers have names that refer to a [configured][config] component using [Typesafe Config][typesafe-config],
- Producers will be implemented using [akka-streams][akka-streams] and are a `ActiveMqSink("producerName")` and need an implicit `MessageBuilder` to produce/send messages,

## Producing/Sending and Creating Messages
The [ActiveMqSink][amqsink] needs an implicit [MessageBuilder][builder] to convert a `T` into a [CamelMessage][msg] that will be used to send
a message to a [VirtualTopic][vt]. 

- To build a message, the typeclass pattern will be used which is a `MessageBuilder[IN, CamelMessage]`,
- `IN` will be defined as a `T` which will be whatever element is flowing though the stream,
- `OUT` will be defined as a [CamelMessage][msg],
- [MessageBuilder][builder] is therefor defined as: `MessageBuilder[T, CamelMessage]`,
- The MessageBuilder is responsible for building the CamelMessage and setting any relevant headers thus all information must be 
  available in `T`, the flowing element, any static information can be injected from global scope,
- Having the MessageBuilder pluggable decouples the marshalling method.

## Removing messages from ActiveMq
A message will be removed from the broker when:
- A message cannot be extracted/unmarshalled by the [ActiveMqSource][amqsource] ie. the [MessageExtractor][extractor] throws an exception,
- The [fmapAck][fmapack], [fmapAsync][fmapasync] combinator is used and the operation succeeds,
- The [runForeachAck][runforeach] operations runs the stream and the enclosed foreach operation succeeds, 
- Any of the normal combinators are used and the function has completed the enclosed promise from the `AckTup[A]` type, which is an alias for `Tuple2[Promise[Unit], A]`, 
- Basically when the promise has been completed with a success somewhere in the stream,

## Leaving messages on ActiveMq
A message will be left on the broker when:
- When the `akka.camel.consumer.reply-timeout = 1m` has been triggered,
- The [fmapAck][fmapack], [fmapAsync][fmapasync] combinator is used and the operation fails,
- The [runForeachAck][runforeach] operations runs the stream and the enclosed foreach operation fails, 
- Any of the normal combinators are used and the function has failed the enclosed promise from the `AckTup[A]` type, which is an alias for `Tuple2[Promise[Unit], A]`, 
- Basically when the promise has been completed with a failure somewhere in the stream,
 
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

# Resources
- [The Need for Acknowledgement in Streams][need-for-ack]
- [op-rabbit][op-rabbit]

# Whats new?
- v0.0.3 (2016-06-29)
  - To initialize connections, a list of connections is added to `reactive-activemq` [config][config] which will be
    initialized as soon as possible as they will be created by the [ActiveMqExtension][extension],
  - Each consumer/producer has its own (free to choose) name and is read from [configuration][config].
- v0.0.2 (2016-06-28)
  - Cleanup some of the code.
- v0.0.1 (2016-06-28)
  - Initial release.

[need-for-ack]: http://tim.theenchanter.com/2015/07/the-need-for-acknowledgement-in-streams.html
[op-rabbit]: https://github.com/SpinGo/op-rabbit
[spingo]: https://www.spingo.com/
[config]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/resources/reference.conf
[extension]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/com/github/dnvriend/activemq/extension/ActiveMqExtension.scala
[builder]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/com/github/dnvriend/activemq/stream/MessageBuilder.scala
[extractor]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/com/github/dnvriend/activemq/stream/MessageExtractor.scala
[amqsource]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/com/github/dnvriend/activemq/stream/ActiveMqSource.scala
[amqsink]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/com/github/dnvriend/activemq/stream/ActiveMqSink.scala
[fmap]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/com/github/dnvriend/activemq/stream/AckedFlowOps.scala#L40
[fmapack]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/com/github/dnvriend/activemq/stream/AckedFlowOps.scala#L27
[fmapasync]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/com/github/dnvriend/activemq/stream/AckedFlowOps.scala#L44
[runforeach]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/com/github/dnvriend/activemq/stream/AckedFlowOps.scala#L55
[msg]: https://github.com/akka/akka/blob/master/akka-camel/src/main/scala/akka/camel/CamelMessage.scala
[vt]: http://activemq.apache.org/virtual-destinations.html
[amq]: http://activemq.apache.org/
[akka-streams]: http://doc.akka.io/docs/akka/current/scala/stream/index.html
[akka-persistence]: http://doc.akka.io/docs/akka/current/scala/persistence.html
[akka-persistence-query]: http://doc.akka.io/docs/akka/current/scala/persistence-query.html
[linear]: http://doc.akka.io/docs/akka/current/scala/stream/stream-flows-and-basics.html#Defining_and_running_streams
[mat]: http://doc.akka.io/docs/akka/current/scala/stream/stream-composition.html#materialized-values
[demand]: http://doc.akka.io/docs/akka/current/scala/stream/stream-flows-and-basics.html#Back-pressure_explained
[typesafe-config]: https://github.com/typesafehub/config
