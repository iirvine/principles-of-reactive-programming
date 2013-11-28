#The Effect of Latency
Recall Erik's four essential effects in programming -
![img](http://i.imgur.com/uJNtpQR.png)

Last week we talked about `Try[T]`, and the effect of exceptions. In this lesson, we're gonna look at asynchronous computations and the effect of latency. We're moving from synchronous to asynchronous computations. Shit's gonna get CRAZY.

Remember our simple adventure game?

```scala
trait Adventure {
    def collectCoins(): List[Coin]
    def buyTreasure(coins: List[Coin]): Treasure
}

val adventure = Adventure()
val coins = adventure.collectCoins()
val treasure = adventure.buyTreasure(coins)
```

We ended up turning our return types for `collectCoins` and `buyTreasure` into `Try[List[Coins]]` and `Try[List[Treasure]]` to reflect the fact that those functions could throw exceptions in the types.

Let's take this same adventure game and apply a homomorphism to it - let's create some *networking* code!

```scala
trait Socket {
    def readFromMemory(): Array[Byte]
    def sendToEurope(packate: Array[Byte]) Array[Byte]
}

val socket = Socket()
val packet = socket.readFromMemory()
val confirmation = socket.sendToEurope(packet)
```

We just renamed some methods, and we've got a simple networking stack! AMAZING.

But things are not as rosy as it looks... when we look at this code in more detail, there's much more going on than the code shows. There's all kinds of effects happening that are not apparent in the types. Just like last time, we're gonna make these types more precise to expose the fact that there are side effects happening when we run this code.

###Timings
![img](http://i.imgur.com/UHROnNZ.png)

The effects we're interested in this week is the effect of latency. Here's a table of timings for various operations on a PC. Here's our code again, but with comments for the effects that are actually happening:

```scala
val socket = Socket()
val packet = socket.readFromMemory()
// block for 50,000 ns
// only continue if there is no exception
val confirmation = socket.sendToEurope()
// block for 150,000,000 ns
// only continue if there is no exception
```

When we read from memory, we're blocked for 50 thousand nanoseconds - we only continue after we've blocked for that time and there is no exception. When we send the packet to europe and back, we're blocked for 150 *million* nanoseconds, and again only after this thing terminates successfully will we move on to the next line.

This is a very very heavy effect. Let's translate these nanoseconds into more human terms: 1 nanosecond -> 1 second.

![img](http://i.imgur.com/mQ3g3Bi.png)

Yikes. Reading one megabyte from memory in human terms takes *3 days*; sending a packet to europe and back takes *5 years* 

![img](http://i.imgur.com/aHHGgEo.png)

This is an effect that we should not leave unaccounted for. We want this effect to be explicit in our types.

###Sequential Composition of Actions that Take Time and Fail
There's this very expensive effect - isn't there a monad for that? Of course there is dummy. 

`Future[T]` is a monad that handles exceptions and latencies. 

```scala
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

trait Future[T] {
    def onComplete(callback: Try[T] => Unit)
    (implicit executor ExecutionContext): Unit
}
```

A `Future` is a computation that will complete in the future; the way that we deal with that is that we give it a callback that will be called as soon as the result of the computation is available. 

As `Future` models computations that have latency as well as exceptions, our callback gets a value of type `Try[T]`. If the computation has failed, that'll be a `Failure`.

One thing we're gonna be a bit implicit about - that implicit parameter. A `Future` typically runs on a background thread, on a threadpool or something. If something takes a long time, we don't want to block on the main thread. In order to route the threadpool through our computation, scala uses an implicit parameter that takes an `ExecutionContext`. We'll leave this implicit, because it's not really necessary to understand `Future`.

###Alternative Future designs
```scala
trait Future[T] {
    def onComplete(sucesss: T => Unit, failed: Throwable => Unit): Unit
    def onComplete(callback: Observer[T]): Unit
}

trait Observer[T] {
    def onNext(value: T): Unit
    def onError(error: Throwable): Unit
}
```

Our other `onComplete` took a function from `Try[T] => Unit`; the first thing we're going to do on a `Try[T]` is a case statement to see if it's a success or a failure. We'll have to define two functions. Instead of passing `Try[T] => Unit`, we can also pass in two functions - one for success, and one for failure. This would be isomorphic to our other `Future`

The second alternative would be to take these two success and failure callback functions and wrap them in their own trait, `Observer`. It's just something that pairs these two callbacks. This would also be the same. So, we can always take a value and replace it with the functions you would use to pattern match on that value.

###Futures asynchronously notify consumers
Let's rewrite our `Socket` trait using `Future`!

```scala
trait Socket {
    def readFromMemory(): Future[Array[Byte]]
    def sendToEurope(packate: Array[Byte]): Future[Array[Byte]]
}
```

Instead of returning a regular byte array, we now return a `Future[Array[Byte]]`. This indicates that reading from memory may take a long time and may fail. Similar for sending a packet to europe. 

Whenever we have a computation that takes a long time, we should always make it return a future - that way the consumer of our values can see in a type that this might take a long time, and they won't block on it.

###Send packets using Futures
once we rewrite our signatures to return `Future`, we also have to adopt our code to pass around `Future` and grab values out of them. How about we just try to use `onComplete` directly?

![img](http://i.imgur.com/8poqvwk.png)

We want to send that array of bytes we got from our socket to Europe, but it's wrapped in a `Future`. How do we get it out? Well, we have our `onComplete` function - we give it two callbacks. When it's success, we can send our packet to europe. When it fails, well, whatever. 

But wait - oh no. We got some red squigglies. This is not type correct. `onComplete` takes a function from `Try[T] => Unit` - so the whole result of our `packet onComplete` call is of type `Unit`. We want it to be of type `Future[Array[Bytes]]`, which is the result of `socket.readFromMemory()`. Unfortunately, the signature of `onComplete` doesn't allow us to return the `Future` as such.

What can we do???

Well, we can push the rest of the computation *inside* the success case... 

![img](http://i.imgur.com/ripwzrH.png)

This isn't really helping us... now we have to move *all* the rest of the computation inside that success callback. If there's more failures and successes, this'll turn into a spaghetti.

###Creating Futures
Let's punt on how to write better code with `Future` for a little bit to look into how we can create futures. Well, like with all traits lets look at the companion object:

![img](http://i.imgur.com/As6MOFv.png)

Our `apply` method takes a `body` and an implicit `ExecutionContext`, and returns a `Future[T]`. 

Let's look at a possible implementation of `readFromMemory`:

![img](http://i.imgur.com/0zbPu4l.png)

We basically just pass in the block of code that may take a long time (where we're reading our emails from memory) into the `Future` constructor to have it run on a background thread.