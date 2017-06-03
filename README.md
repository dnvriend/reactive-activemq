# reactive-activemq #
[![Download](https://api.bintray.com/packages/dnvriend/maven/reactive-activemq/images/download.svg)](https://bintray.com/dnvriend/maven/reactive-activemq/_latestVersion)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

reactive-activemq is an [akka-streams][akka-streams] compatible connector for [ActiveMq][amq] providing two components, the [ActiveMqConsumer][amqconsumer] and [ActiveMqProducer][amqproducer] that can consume and produce messages with [VirtualTopic][vt] semantics, using [akka-streams][akka-streams]'s [demand stream][demand] feature to control the message flow between components. This project is very much work in progress.

# Notice
This project is not being maintained and/or actively developed anymore. For a more modern, scalable and production ready solution please use [Apache Kafka](http://kafka.apache.org) or better yet the [Confluent Platform](https://www.confluent.io/product/confluent-open-source/) in combination with [Reactive Kafka](https://github.com/akka/reactive-kafka) or [Kafka Streams](https://www.confluent.io/blog/introducing-kafka-streams-stream-processing-made-simple/).

## Installation
Add the following to your `build.sbt`:

```scala
resolvers += Resolver.jcenterRepo

libraryDependencies += "com.github.dnvriend" %% "reactive-activemq" % "0.0.27"
```

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License ##

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Why use it?
Good question! The project is very new, so only use it when you really like the [akka-streams][akka-streams] API. I use it to combine consuming messages from [ActiveMq][amq] with [akka-persistence][akka-persistence] and/or [akka-persistence-query][akka-persistence-query] API to [ActiveMq][amq]. This way I don't have to think about configuring Camel consumers, request/response semantics or Acking messages.

### But why though?
ActiveMq is an open source message broker written in Java that supports enterprise messaging which means that it intermediates when more than one component should be notified about changes happening in the system using a concept called Messaging in which components notify each other using an intermediary also called a Broker.

### Data flow architecture
Components architected with streaming in mind leverage the akka-streams abstraction in which messages are flowing though systems using data flow architecture. In the data flow architecture, an ActiveMq 'queue' has been abstracted as a stream of messages because the contents of a queue is ever changing and is never the same. When using akka-streams, ActiveMq gets the role of a Source, a component that generates messages, a Source of messages if you will,  when messages are flowing from one component to the next to be processed, or ActiveMq can get the role of a Sink, a receiver of messages when messages have been processed by a component and are ready to flow to another component. ActiveMq is the entity responsible for reliably receiving a message, persisting it for some time on its local storage and delivering the message to one or more consumers. It can be tought of as a postoffice that is very reliable.

### Transactions
Consumers use transactions to consume messages from the VirtualTopic. This means that messages can be received by a consumer, but the message will not be removed from ActiveMq until the consumer sends an explicit success to ActiveMq to signal that it is now safe to remove the message. When the consumer sends an explicit failure, signalling to ActiveMq that there was a failure while processing the message, ActiveMq will *not* remove the message. The consumer can now try to consume the message again and when successful it can reply with a success signalling that it is now safe to remove the message.

When a consumer is processing a received message, the system uses a configurable timeout in which the consumer *must* reply with either a success or a failure. When the consumer does not reply within the configurable timeout window, the transaction fails and the message will stay on ActiveMq. The timeout can be configured with the following setting:

```
akka.camel.consumer.reply-timeout = 5m
```

### Idempotency
In a complex messaging system, it is possible that a message can be received zero to more than one time. To be able to have reliable messaging, interaction between the producer of a message and a consumer of a message is needed. When a consumer has not signaled a success or a failure, or a consumer did not process the message to completion because of an error for example the component crashed, the message can be consumed. It is important that the consumer is responsible for detecting whether or not a message has been processed before, and if so handle accordingly. This process is called Idempotency and must be incorporated by every consumer of a message. When a consumer has incorporated a strategy for detecting message duplications, it is very safe for a producer of a message to send the message again when it deems to. This interaction between the producer and consumer makes it possible to create a system that incorporates reliable messaging with exactly once message semantics, because it will process the message only one time, and detect message duplication.

### Reliable Messaging
When sending messages to ActiveMq, the system can be thought of as a reliable messaging system. This means that when a component has successfully sent a message that has been accepted by ActiveMq, the component can assume that the message will eventually be received by one or more components. All messages send to ActiveMq use the Persistent Messaging configuration which means that ActiveMq will store messages on disk preventing data loss and improving reliability. It is not necessary for the system to require an explicit business acknowledgement from another component as the data flow architecture will couple business flows, which means that the process will figure out a possible outcome based on messages received.

### Higher abstractions
When using akka-streams the first thing you notice is that the API is very simple but is also very powerful. Akka-streams makes it possible to use one API to connect components using data flow architecture leveraging dynamically materializing process flows across multiple distributed components. When there there is no more need to worry about how components should be accessed because we can just 'connect' a hose to it just like a garden hose to a water tap, we can a universially abstraction in wich there is more time to focus on the problem domain and just click components together using akka-stream, akka-http, akka-persistence and the multitude of stream combinators and extensions.

## Components
reactive-activemq provides the following components:

## akka.stream.integration.activemq.extension.ActiveMqExtension
The `ActiveMqExtension` configures, initializes and registers one or more `org.apache.activemq.camel.component.ActiveMQComponent`(s) in the CamelContext. The `ActiveMqConsumer` and `ActiveMqProducer` use these ActiveMqComponent(s) and reference them by name eg: `amq`. You will need at least one component to consume and/or produce messages. Each ActiveMqConsumer and ActiveMqProducer have names and can be configured using Typesafe Configuration. For example:

```
akka {
  extensions = ["akka.stream.integration.activemq.extension.ActiveMqExtension"]
}

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

## akka.stream.integration.activemq.ActiveMqConsumer
A component should be able to be notified about events that has happened in the system. To receive and send messages the architecture uses asynchronous messaging using VirtualTopic semantics. This means that producers send messages to a VirtualTopic. Consumers should subscribe themselves to queues using VirtualTopic semantics, in order to be able to receive a message that has been published by a publisher to a VirtualTopic. This means that potentially a number of consumers can receive a message.

`akka.stream.integration.activemq.ActiveMqConsumer`(s) consume/receive messages using VirtualTopic semantics using the following convention:

```
activemq:queue:Consumer.$CONSUMER_NAME.VirtualTopic.$VIRTUAL_TOPIC_NAME
```

The convention means that consumers use a name when they subscribe themselves to ActiveMq as a consumer that is interested in messages that will be produced by a certain Producer to a VirtualTopic. An administrator can log on to ActiveMq and get a list of consumers that are published to a certain VirtualTopic.

The `akka.stream.integration.activemq.ActiveMqConsumer` consumes messages from an ActiveMq VirtualTopic in such a way that each message must be acknowledged before they are removed from the broker. Messages can be acknowledged by connecting the ActiveMqConsumer to an `akka.stream.integration.activemq.AckSink`, that consumes messages and acks each messages. The AckSink contains some of the operations we know from akka-streams like `AckSink.seq` that acks and collects each incoming elements until the stream terminates and returns a Seq[T], the `AckSink.foreach` that acks each message and applies the given function to the payload of the message and `AckSink.complete` that completes each incoming message but does nothing to the payload, effectively being an ordinary Sink.ignore, but one that acks.

The ActiveMqConsumer can also be connected to an `akka.stream.integration.activemq.AckFlow` that contains some of the operations we know from akka-streams eg. `AckFlow.map` that acks each message, and applies the function to each message it receives. The `AckFlow.mapAsync` that does the same but it needs a function from A => Future[B], can be handy for interacting with services like shard regions, actors, web services or DAOs. The `AckFlow.filter`, that acks each message for which the predicate satisfies, effectively removes the messages from the broker.

The ActiveMqConsumer needs an implicit `akka.stream.integration.CamelMessageExtractor` to convert an `akka.camel.CamelMessage` to an 'A'. As a CamelMessage can contain Any type, and content to be extracted can be in the body but also encoded in header values, the CamelMessageExtractor should be used to convert the CamelMessage into a proper type.

There are a lot of Builders and Extractors defined as type classes, some of them work out of the box like the `akka.stream.integration.JsonCamelMessageExtractor` and need an implicit JsonReader and optionally a HeaderExtractor in combination with a Semigroup to extract the relevant content of the CamelMessage.

The `akka.stream.integration.JsonCamelMessageBuilder` needs an implicit JsonWriter and optionally an implicit HeadersBuilder to create a CamelMessage that can be used by an ActiveMqProducer.

Using type classes can really clean up your code as they are only converters and have not a whole lot to do with the business logic and as such can be cleaned up nicely using this pattern.

I would advice though to use Protobuf as the messaging format of choice as it abstracts message versioning, which is a big deal when communicating between services that have a life cycle of their own. To support Protobuf there is a ProtobufCamelMessageBuilder and a ProtobufCamelMessageExtractor defined that uses the ProtobufFormat which works just like spray-json using type classes.

## akka.stream.integration.activemq.ActiveMqProducer
A component should be able to send notifications about changes that has happened in the system. To be able to send messages the architecture uses VirtualTopic semantics. This means that producers send messages to a VirtualTopic that consumers can receive. In order to be able to send messages to a VirtualTopic, the system uses the following convention:

```
activemq:topic:VirtualTopic.$VIRTUAL_TOPIC_NAME
```

The convention means that producers send messages to a topic and consumers receive messages from that topic only when they have subscribed themselves using their name and using the VirtualTopic name.

An `ActiveMqProducer` is a component that has a name, eg: `PersonProducer` and can be used as follows:

```scala
import akka.Done
import akka.actor.ActorSystem
import akka.camel.CamelMessage
import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.integration.activemq._

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()
implicit val ec: ExecutionContext = system.dispatcher
sys.addShutdownHook(system.terminate())

val fut: Future[Done] = Source.repeat(testPerson1).take(numberOfPersons).runWith(ActiveMqProducer("PersonProducer"))
```

The ActiveMqProducer needs an implicit `akka.stream.integration.CamelMessageBuilder[A]` to create a CamelMessage that contains a body and header(s) to send. The ActiveMqProducer can be used as a Sink or Flow by using the methods on the `ActiveMqProducer` object the desirable materialization option can be selected, either sink, flow or by default using the apply() to create a sink.

## akka.stream.integration.activemq.ActiveMqFlow
TBD

### BidiFlows
TBD

# Whats new?
- v0.0.27 (2017-01-09)
  - Akka 2.4.9 -> 2.4.16
  - Merged PR #7 [Merlijn Boogerd][mboogerd] - Update dependencies, thanks!
  - Merged PR #6 [Merlijn Boogerd][mboogerd] - Allow overriding the name of consumers using a name configuration key, thanks!

- v0.0.25 (2016-08-20)
  - Akka 2.4.8 -> 2.4.9

- v0.0.24 (2016-07-20)
  - Replaced `MessageExtractor[CamelMessage, A]` with a `CamelMessageExtractor[A]` to make use of the context bound syntax.
  - Introduced the HeadersBuilder and HeadersExtractor to create headers / extract headers.
  - Altered the JsonCamelMessageBuilder and JsonCamelMessageExtractor, both support the (optionally) new HeadersBuilder/HeadersExtractor in combination with the Semigroup typeclass to consume / create a CamelMessage,
  - Added support for sending/receiving `com.google.protobuf.Message`(s) using the `ProtobufCamelMessageBuilder` and `ProtobufCamelMessageExtractor` using the `ProtobufFormat` and HeadersBuilder/HeadersExtractor typeclasses.

- v0.0.23 (2016-07-18)
  - Split the io, xml and persistence extensions to a new project called 'akka-persistence-query-extensions', which is of course unofficial.

- v0.0.22 (2016-07-15)
  - Resumable Query; should wait for demand then send the latest offset.

- v0.0.21 (2016-07-15)
  - ResumableQuery uses a better strategy that leverages backpressure

- v0.0.20 (2016-07-12)
  - Merged PR #4 [Merlijn Boogerd][mboogerd] - Extracted configuration from ActiveMqExtension, thanks!

- v0.0.19 (2016-07-12)
  - Resumable query, changed the OUTPUT of the flow to from EventEnvelope to Any.

- v0.0.18 (2016-07-12)
  - Resumable query, create the writer on global global scope (not in stage), also there
   is *no* guarantee that messages will be delivered exactly-once. The interface of the
   ResumableQuery should be an EventEnvelope as the non-resumable akka-persistence-query are too.
   Also the EventEnvelope is created for the client to implement exactly-once handling of messages ie.
   the persistenceId/seqNo/offset meta is available to be used exactly for that purpose.

- v0.0.17 (2016-07-11)
  - Optimalization to Journal component

- v0.0.16 (2016-07-09)
  - Refactored package structure
  - Journal, AckJournal and ResumableQuery have unit tests,
  - Change to Journal, AckJournal and ResumableQuery flow API, using refactored bidi-flow
    for a more user-friendly API; you don't have to worry about the tuple, this grealy simplifies
    using the component.

- v0.0.15 (2016-07-07)
  - Added `com.github.dnvriend.stream.io.FileUtils` component that can:
    - Check whether or not a file exists,
    - Delete a file,
    - Move a file.

- v0.0.14 (2016-07-07)
  - Merged PR #3 [Merlijn Boogerd][mboogerd] - Refactored bidi-flow, implemented request-response, thanks!

- v0.0.13 (2016-07-06)
  - fix bug in snapshotting

- v0.0.12 (2016-07-06)
  - Default snapshot size (500)

- v0.0.11 (2016-07-05)
  - The Journal and AckJournal do not need a name, they exist only to optionally tag events and store messages in
    the journal to be used by `EventsByTagQuery` and `CurrentEventsByTagQuery` akka-persistence-query API.
  - Snapshotting for the `com.github.dnvriend.stream.persistence.ResumableQuery`.

- v0.0.10 (2016-07-05)
  - Changed the interface of the Journal and AckJournal

- v0.0.9 (2016-07-05)
  - Refactored the ActiveMq API to become more friendly and intuitive.
  - Todo: documentation and API docs

- v0.0.8 (2016-07-04)
  - Added `com.github.dnvriend.stream.xml.Validation`, a component that can validate a XML document against a XSD
  - Added `com.github.dnvriend.stream.persistence.ResumableQuery`, a component that can resume a stopped persistence-query

- v0.0.7 (2016-07-02)
  - Added `com.github.dnvriend.stream.io.DigestCalculator`, a flow that can calculate digest based on Source[ByteString, NotUsed], supports MD2, MD5, SHA-1, SHA-256, SHA-384 and SHA-512.

- v0.0.6 (2016-07-01)
  - Merged PR #2 [Merlijn Boogerd][mboogerd] - Added component `AckBidiFlow`, thanks!
  - Added `AckBidiFlow`, which is a naive implementation of a bidirectional flow from/to ActiveMq; it assumes:
    - a 1 on 1 correspondence ([bijection][bijection]) between items sent from Out and received on In,
    - that ordering is preserved between Out and In; i.e. no mapAsyncUnordered, ideally no network traversals; careful with dispatching to actors,
    - that at-least-once-delivery is acceptable on ActiveMqSink,
    - The `AckBidiFlow` flow is practical for the typical use case of handling a request received from ActiveMq,
      processing it with some bidi-flow, and dispatching a response to ActiveMq. The original requests gets acked
      once the response is sent.

- v0.0.5 (2016-07-01)
  - Refactored package structure
  - Added `CamelActorPublisher`
  - Added `XMLEventSource`, a `Source[XMLEvent, NotUsed]` that reads a file or inputstream and
    emits `scala.xml.pull.XMLEvent` for processing large XML files very fast with efficient memory usage.

- v0.0.4 (2016-06-30)
  - Added two new components, the `AckJournal` and`Journal`.

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
[vt]: http://activemq.apache.org/virtual-destinations.html
[amq]: http://activemq.apache.org/
[akka-streams]: http://doc.akka.io/docs/akka/current/scala/stream/index.html
[akka-persistence]: http://doc.akka.io/docs/akka/current/scala/persistence.html
[akka-persistence-query]: http://doc.akka.io/docs/akka/current/scala/persistence-query.html
[linear]: http://doc.akka.io/docs/akka/current/scala/stream/stream-flows-and-basics.html#Defining_and_running_streams
[mat]: http://doc.akka.io/docs/akka/current/scala/stream/stream-composition.html#materialized-values
[demand]: http://doc.akka.io/docs/akka/current/scala/stream/stream-flows-and-basics.html#Back-pressure_explained
[typesafe-config]: https://github.com/typesafehub/config

[mboogerd]: https://github.com/mboogerd
[bijection]: https://en.wikipedia.org/wiki/Bijection

[extension]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/akka/stream/integration/activemq/extension/ActiveMqExtension.scala
[builder]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/akka/stream/integration/MessageBuilder.scala
[extractor]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/akka/stream/integration/MessageExtractor.scala
[amqconsumer]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/akka/stream/integration/activemq/ActiveMqConsumer.scala
[amqproducer]: https://github.com/dnvriend/reactive-activemq/blob/master/src/main/scala/akka/stream/integration/activemq/ActiveMqProducer.scala
[msg]: https://github.com/akka/akka/blob/master/akka-camel/src/main/scala/akka/camel/CamelMessage.scala
