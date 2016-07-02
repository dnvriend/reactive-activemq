# Notes

# GraphDSL vs GraphStage
See: [Custom stream processing][custom-stream]

##
What are the differences?
- The GraphDSL.create() method createss a new stream processing stage by composing others.
  - It `composes stages` into a new one

- GraphStage creates a stage that is itself not divisible into smaller ones, and allows state to be maintained inside it in a safe way.
  - A single stage that can be stateful

# GraphStage examples
## Emitting an element from a SourceShape
- SourceShapes emit elements on the output port.
- In order to emit from a `Source`, we first need to have demand from downstream.
- To receive the demand request we need to register a subclass of an `OutHandler` with the `output` port (Outlet).
- This handler will receive events related to the lifecycle of the port.
- When there is demand, the `onPull()` method will be called (callback method) to indicate that we are free to emit a single element,
- When there is demand, an element can be pushed to the output port with `push(port, elem)`
- In the `onPull()` callback we will emit the next number by calling `push(port, elem)`.
- To indicate that downstream has cancelled the stream, the `onDownstreamFinish()` callback is called.
- The default behavior of that callback is to stop the stage, we don't need to override it.

```scala
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}

class NumbersSource extends GraphStage[SourceShape[Int]] {
  val out: Outlet[Int] = Outlet("NumbersSource")
  override val shape: SourceShape[Int] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      private var counter = 1

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          push(out, counter)
          counter += 1
        }
      })
    }
}
```

## Handlers
- read: [Port states, InHandler and OutHandler][handlers]
- A stage has ports (Inlet, Outlet).
- In the __Graphstage__ we define ports using `val`s,
- Interacting with these ports (Inlet or Outlet) is done via the __GraphStageLogic__ class,
- The __GraphStageLogic__ can contain state,
- The __GraphStageLogic__ can register an __OutHandler__ on __Outlet__s and __InHandler__ on __Inlet__s,

## OutHandler
- __OutHandler__s handle events corresponding to an output port:
  - Events can be received by an OutHandler _instance_ registered to the output port using `setHandler(out,handler)`,
  - Note that the handler instance can be switched by calling `setHandler(out,handler)` with another OutHandler reference,
  - The OutHandler has the following operations:
    - `push(out,elem)`:` pushes an element to the output port. Only possible after the port has been pulled by downstream.
    - `complete(out)`:` closes the output port normally.
    - `fail(out,exception):` closes the port with a failure signal.
  - The OutHandler has the following callbacks:
    - `onPull()` is called when the output port is ready to emit the next element, push(out, elem) is now allowed to be called on this port.
    - `onDownstreamFinish()` is called once the downstream has cancelled and no longer allows messages to be pushed to it. No more onPull() will arrive after this event. If not overridden this will default to stopping the stage.
  - There are two query methods available for output ports:
    - `isAvailable(out)` returns true if the port can be pushed
    - `isClosed(out)` returns true if the port is closed. At this point the port can not be pushed and will not be pulled anymore.

## InHandler
- __InHandler__s handle events corresponding to an input port:
  - These events can be received by an Inhandler _instance_ registered to the output port using `setHandler(in,handler)`,
  - Note that the handler instance can be switched by calling `setHandler(in,handler)` with another InHandler reference,
  - The InHandler has the following operations:
    - `pull(in)` requests a new element from an input port. This is only possible after the port has been pushed by upstream.
    - `grab(in)` acquires the element that has been received during an onPush(). It cannot be called again until the port is pushed again by the upstream.
    - `cancel(in)` closes the input port.
  - The OutHandler has the following callbacks:
    - `onPush()` is called when the output port has now a new element. Now it is possible to acquire this element using grab(in) and/or call pull(in) on the port to request the next element. It is not mandatory to grab the element, but if it is pulled while the element has not been grabbed it will drop the buffered element.
    - `onUpstreamFinish()` is called once the upstream has completed and no longer can be pulled for new elements. No more onPush() will arrive after this event. If not overridden this will default to stopping the stage.
    - `onUpstreamFailure()` is called if the upstream failed with an exception and no longer can be pulled for new elements. No more onPush() will arrive after this event. If not overridden this will default to failing the stage.
  - There are three query methods available for output ports:
    - `isAvailable(in)` returns true if the port can be grabbed.
    - `hasBeenPulled(in)` returns true if the port has been already pulled. Calling pull(in) in this state is illegal.
    - `isClosed(in)` returns true if the port is closed. At this point the port can not be pulled and will not be pushed anymore.

## Convenience operations
There are two methods available for convenience to complete the stage and all of its ports:
- `completeStage()` is equivalent to closing all output ports and cancelling all input ports.
- `failStage(exception)` is equivalent to failing all output ports and cancelling all input ports.

## GraphStage operators:
In some cases it is inconvenient and error prone to react on the regular state machine events with the signal based
API described above. For those cases there is a API which allows for a more declarative sequencing of actions which will
greatly simplify some use cases at the cost of some extra allocations.

The difference between the two APIs could be described as that the first one is signal driven from the outside,
while this API is more active and drives its surroundings.

- emit(out, elem) and emitMultiple(out, Iterable(elem1, elem2)) replaces the OutHandler with a handler that emits one or more elements when there is demand, and then reinstalls the current handlers
- read(in)(andThen) and readN(in, n)(andThen) replaces the InHandler with a handler that reads one or more elements as they are pushed and allows the handler to react once the requested number of elements has been read.
- abortEmitting() and abortReading() which will cancel an ongoing emit or read

Note that since the above methods are implemented by temporarily replacing the handlers of the stage
you should never call setHandler while they are running emit or read as that interferes with how they
are implemented. The following methods are safe to call after invoking emit and read (and will lead to
actually running the operation when those are done): complete(out), completeStage(), emit, emitMultiple,
abortEmitting() and abortReading()

# ByteString
To maintain isolation, actors should communicate with immutable objects only. [ByteString][bytestring] is an immutable container for bytes.
It is used by Akka's I/O system as an efficient, immutable alternative the traditional byte containers used for I/O on the JVM,
such as Array[Byte] and ByteBuffer.

ByteString is a rope-like data structure that is immutable and provides fast concatenation and slicing operations (perfect for I/O).
When two [ByteStrings][bytestring] are concatenated together they are both stored within the resulting [ByteString][bytestring]
instead of copying both to a new Array. Operations such as drop and take return [ByteStrings][bytestring] that still reference
the original Array, but just change the offset and length that is visible. Great care has also been taken to make sure that the
internal Array cannot be modified. Whenever a potentially unsafe Array is used to create a new [ByteString][bytestring] a defensive
copy is created. If you require a [ByteString][bytestring] that only blocks as much memory as necessary for it's content,
use the compact method to get a CompactByteString instance. If the [ByteString][bytestring] represented only a slice of the
original array, this will result in copying all bytes in that slice.

[ByteString][bytestring] inherits all methods from IndexedSeq, and it also has some new ones. For more information,
look up the [akka.util.ByteString] class and it's companion object in the ScalaDoc.

[ByteString][bytestring] also comes with its own optimized builder and iterator classes ByteStringBuilder and ByteIterator
which provide extra features in addition to those of normal builders and iterators.

## Compatibility with `java.io`
A ByteStringBuilder can be wrapped in a java.io.OutputStream via the asOutputStream method. Likewise,
ByteIterator can be wrapped in a java.io.InputStream via asInputStream. Using these, akka.io applications
can integrate legacy code based on java.io streams.

# akka.stream.scaladsl.StreamConverters
Converters for interacting with the blocking `java.io` streams APIs and Java 8 Streams

# Misc notes

- akka.stream.scaladsl.FileIO
Factory method to create a new sink eg: to creates a Sink which writes incoming [ByteString][bytestring] elements to the given file.
Overwrites existing files by default.

```scala
def toPath(f: Path, options: Set[StandardOpenOption] = Set(WRITE, CREATE)): Sink[ByteString, Future[IOResult]] =
  new Sink(new FileSink(f, options, DefaultAttributes.fileSink, sinkShape("FileSink")))
}
```

- akka.stream.impl.io.FileSink
The `FileSink` creates simple synchronous Sink which writes all incoming elements to the given file (creating it before hand if necessary).
It is a `SinkModule[ByteString, Future[IOResult]]`

- akka.stream.impl.io.FileSubscriber
An ActorSubscriber that does the actual work

... but.. all of this are all internal for akka only APIs :(

- [Java Cryptography Architecture Sun Providers Documentation][JCA]


[bytestring]: http://doc.akka.io/docs/akka/current/scala/io.html#ByteString
[custom-stream]: http://doc.akka.io/docs/akka/current/scala/stream/stream-customize.html
[handlers]: http://doc.akka.io/docs/akka/current/scala/stream/stream-customize.html#Port_states__InHandler_and_OutHandler
[cookbook]: http://doc.akka.io/docs/akka/current/scala/stream/stream-cookbook.html
[JCA]: http://docs.oracle.com/javase/6/docs/technotes/guides/security/SunProviders.html