# reactive-activemq #

[![Join the chat at https://gitter.im/dnvriend/reactive-activemq](https://badges.gitter.im/dnvriend/reactive-activemq.svg)](https://gitter.im/dnvriend/reactive-activemq?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/dnvriend/reactive-activemq.svg?branch=master)](https://travis-ci.org/dnvriend/reactive-activemq)
[![Download](https://api.bintray.com/packages/dnvriend/maven/reactive-activemq/images/download.svg)](https://bintray.com/dnvriend/maven/reactive-activemq/_latestVersion)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

reactive-activemq is an [akka-streams][akka-streams] compatible connector for [ActiveMq][amq] providing two
components, the [ActiveMqConsumer][amqconsumer] and [ActiveMqProducer][amqproducer] that can consume and produce messages with [VirtualTopic][vt] semantics, using [akka-streams][akka-streams]'s [demand stream][demand] feature to control the
message flow between components. This project is very much work in progress.

## Installation
Add the following to your `build.sbt`:

```scala
resolvers += Resolver.jcenterRepo

libraryDependencies += "com.github.dnvriend" %% "reactive-activemq" % "0.0.17"
```

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License ##

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

# Whats new?
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
