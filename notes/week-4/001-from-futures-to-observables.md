#From Futures to Observables

From last week - `Future` helps us write code that is asynchronous, but only return a single result. Now, we're going to turn `Future`s into `Observable`s, which are asynchronous *streams* of data - collections which asynchronously produce their values.

![img](http://i.imgur.com/Xvtx0Ps.png)

Remember our little diagram? Now we're working with the _Many_ side of the table - computations that either synchronously or asynchronously return multiple values. 

Let's look a little bit more in-depth at `Future` and `Try`. There are still a few more things to discover. 

In particular, what we're going to show is that `Future[T]` and `Try[T]` are *dual* - that sounds really complicated. Erik says it's a lot of fun.

Let's start by looking at our `Future[T]` trait:

```scala
trait Future[T] {
    def onComplete[U](func: Try[T] => U)(implicit ex: ExecutionContext): Unit
}
```

it had one method: `onComplete`, which takes a function `Try[T] => U`, an implicit `ExecutionContext`, and returns `Unit`. This callback function receives the single value when the `Future` completes. 

traits, definitions, whatever - if we simplify this type, we get a function that takes a function `Try[T] => Unit`. That's really all a `Future` is - a function that takes a callback `Try[T] => Unit`, and returns `Unit`. That's really the essence of a `Future`. So, it simplifies to:

`(Try[T] => Unit) => Unit`

Now, let's squeeze this type and turn it into something different. First, let's flip the arrows. We'll turn the arrows of our simplified `Future` function around - the `Unit` that's the result of the passed in function becomes the result, and the `Try[T]` becomes the result. Same for the `Unit` that we returned - it's now the argument of the function

`Unit => (Unit => Try[T])`

![img](http://i.imgur.com/IPRGh4z.png)

Now, this signature looks a little unsual. Instead of taking unit, typically we use a function that takes no parameters. We'll rewrite this a little bit. 

`() => (() => Try[T])`

we can simplify this even further by noticiing that a `Future` only returned a single value - when we called `onComplete` multiple times on a `Future`, we know that it always returned the same value. So `Future` is idempotent. In this case, we can use that to simplify this expression even further. If the `Try[T]` is always the same value, we can just represent it immediately:

`Try[T]`

What did we arrive at? Well, here we've got our asynchronous function that returns a `Future`:

```scala
def asynchronous(): Future[T] = {...}
```

How do we communicate that? Well, we pass a callback `Try[T] => Unit` to this `Future`. Or, we can do a synchronous method, which returns a `Try[T]`, and *blocks* until the result has been returned:

```scala
def synchronous(): Try[T] = {...}
```

So, `Future[T]` and `Try[T]` are dual.

Want to learn a bit more? Well, get yourself some category theory ya jerk. 

Now what we're going to do is see if we can play the same trick with computations that return multiple results... but before we do that, let's revisit our old friend `Iterable`

###Synchronous Data Streams: Iterable[T]

```scala
trait Iterable[T] { def iterator(): Iterator[T] }

trait Iterator[T] { def hasNext: Boolean; def next(): T }
```

`Iterable` consists of two types - there's `Iterable`, which has a single method `Iterator`. The `Iterator` is the one that has the `next` method, that gives the next value in the stream, and the `Boolean` `hasNext` that tells us when the stream still has more elements.

![img](http://i.imgur.com/Aa8jiPi.png)

Here's our little hero again. How does he get his coins out of the `Iterable[Coin]`? Well, he calls `iterator()`. That gives him an `Iterator[Coin]`. Then, `while(hasNext) next()`, which gives him the next coin. 

Notice that he's *pulling* the values out of this `Iterator` - everytime we call next, we're blocking until the next coin comes out. 

Let's look further in the source of `Iterable`:

![img](http://i.imgur.com/Topunrp.png)

Most important, of course, is `flatMap`. It takes an `Iterable` and a function that for each element in the `Iterable` returns another `Iterable`, and then flattens all these streams into a result stream of type `B`.

Another way of saying this is that *iterable is a monad*. Of course we all knew that.

###Marble Diagrams

![img](http://i.imgur.com/EyO0B2Q.png) 

In order to talk nicely about collections we're going to use *marble diagrams*. They show the values of a collection on an arrow. In this case, we have a collection of coins, and a function that turns coins into diamonds. When we map that function over the input collection of coins, we get an output collection of diamonds. The rich get richer.

Let's write some code using `Iterables` to read values from a disk... wait, how long did that take again on our adjusted time-scale? 

 ![img](http://i.imgur.com/moL2UCR.png)

 We'll see that it's maybe not such a good idea to use iterables for computations that take a long time....

 ```scala
 def ReadLinesFromDisk(path: String): Iterator[String] = {
    Source.fromFile(path).getLines()
}

val lines = ReadLinesFromDisk("\c:\tmp.txt")

for (line <- lines) {
    DoWork(lines) ...
}
 ```

Notice that we're reading the line from disk... say each line is a few hundred kilobytes. With our adjusted time scale, it'll take two weeks to get a line from disk.

![img](http://i.imgur.com/uaKZque.png)

Instead what we need to do, just like with `Future`, we have to start doing asynchronous IO. 

###Dualization MAGIX

To do this we'll do the same dualization trick that we used for `Future`. Let's look at our `Iterable` and `Iterator` traits and convert them into their duals:

```scala
trait Iterable[T] {
    def iterator(): Iterator[T]
}

trait Iterator[T] {
    def hasNext: Boolean
    def next(): T
}
```

![img](http://i.imgur.com/8bY4Jxk.png)

We'll start by boiling these down to their essence - instead of having traits we'll just describe them using functions. 

The `iterator` method takes no arguments and returns an `iterator` - so, we write a function that takes no arguments, and returns the boiled-down version of `Iterator`. What's that?

Well, it's something either has a `Boolean` or a `[T]`, which we really can model with an `Option[T]`. Intead of having these two functions, we can just have a function that returns an `Option[T]`. we have to be a little more careful, because `next` can throw an exception if `hasNext` returns false. Really what we should do is say that `Iterator` corresponds to a function that has no arguments and returns a `Try[Option[T]]`:

`(() => Try[Option[T]])`

That corresponds to the fact that the methods on `Iterator` can either throw (`Try`) or return a `T`, or return Nothing. (`Option[T]`)

This type is quite complicated - it's like a composition of two types that are already subtypes. Let's make this simpler. Instead of having a value of `Try[Option[T]]`, we're going to represent this value by the *callback functions* you would give when you pattern-match on a value of this type. 

First, we're going to flip those arrows. 

![img](http://i.imgur.com/6dEeQng.png)

As we flip the arrows, a function that takes no arguments becomes a function that returns `Unit`. But we see here we just reversed the arrows like we did when we were massaging `Future`.

There's our complicated `Try[Option[T]]`. We'll replace this type with the set of functions we need to pass when we're pattern matching on that type - what do we need to do when we're pattern matching on that type? 

Well, there's three posibilities:

* the inner value might be an exception, so we need to pass in a function from `Throwable => Unit`
* if it was not an exception, the result we get is a `Success(Option[T])`. This means we have to pass two functions - one in case there is a value, and one in case there isn't. So we end up passing in `T => Unit` and `() => Unit`

So we end up with the following type:

```scala
( T => Unit,
  Throwable => Unit
  () => Unit
) => Unit
```

It takes three callbacks and returns `Unit`. Now, let's unsimplify it - complexify it. Instead of all these functions, let's define a few traits that capture the essence of this. 

```scala
trait Observable[T] {
    def Subscribe(observer: Observer[T]): Subscription
}

trait Observer[T] {
    def onNext(value: T): Unit
    def onError(error: Throwable): Unit
    def onCompleted(): Unit
}

trait Subscription {
    def unsubscribe(): Unit
}
```

There we go. First, we have a new trait `Observable`. It's the thing that corresponds to a trait that takes the three functions (`T => Unit, Throwable => Unit, () => Unit`) as an argument. 

Those three functions in turn become the `Observer` type. 

Instead of returning `Unit`, we fudge a bit and return `Subscription`. What's that? well, unlike `Future` where we didn't have a notion of cancellation, when we have a stream we might be talking about infinite results. If we pass a set of callbacks to this stream to receive the values, at some point we might want to signal to the producer that we're not interested in receiving anymore values. 

So, when we pass our `Observer` to an `Observable`, we get back a type that represents that subscription. When we call `unsubscribe`, we tell the producer we're dunsies and don't want anymore values. 

###Iterable[T] and Observable[T] are dual

![img](http://i.imgur.com/FscT3od.png)

Hey, these things look very similar - just kinda swapped around!

So, where's the difference between one and many? How do `Future`s relate to `Observable`s?

`Observable[T] = (Try[Option[T]] => Unit) => Unit`
`Future[T] = (Try[T] => Unit) => Unit`

We'll ignore `Subscription` for now - we're not really interested in the cancellation part.

What we see in the difference of these types: when we have a `Future` it returns only a single value. That value can either be an exception, or an actual value. 

In an `Observable`, we can have multiple values or an exception. To be able receive multiple values, we need a way to indicate that there will be *no more values* - that's where the `Option[T]` comes in. 

Because there's no option in `Future[T]`, the only thing we can really do is produce a single value - but once we have a `Try[Option[T]]`, we have the ability to have multiple values, because we can signal whether there's an error, whether the stream has terminated, or whether there's a regular value produced next.

###What about concurrency?

![img](http://i.imgur.com/MPlicae.png)

`Future` has this notion of an implicit `ExecutionContext` in which the body of the `Future` will run. How does this work with `Observable`? They're asynchronous, so there must be some way of doing things concurrently...

Erik says uh-huh, but because there are many results we cannot have simple `ExecutionContext` - we need something more complicated, the `Scheduler`. We'll circle back to that.

###Hello Observables

```scala
val ticks: Observable[Long] = Obsevable.interval(1 seconds)
```

The easiest way to think about an asynchronous stream is as a clock, or a timer. Here, we have an `interval` function that takes a duration. Basically, create an observable stream that returns a `Long` every single second. Since observables have the same operators as any other collection, we can take that stream of `ticks` and filter it:

```scala
val evens: Observable[Long] = ticks.filter(s => s % 2 == 0)
```

Then we can do even fancier things, like buffering that stream by chopping it up into slices of two, shifted by one:

```scala
val bufs: Observable[Seq[Long]] = ticks.buffer(2,1)
```

Then, we subscribe to that stream and do stuff with it, like print out the values:

```scala
val s = bufs.subscribe(b => printLn(b))
readLine()
s.unsubscribe()
```

Let's see what it does:

![img](http://i.imgur.com/IfPCWSI.png)

Our `tick` stream will take one second before it produces the first tick, then another second before the second tick - those are the lines between the marbles. 

When we filter all the ones that are even, we end up with a stream of numbers that are *two* seconds apart, since all the odd ticks are being filtered out.

In the last diagram, the buffer takes segments of length 2 shifted by 1. Wait, what? Basically, we're getting two values with two segments in between. Here's a picture:

![img](http://i.imgur.com/pvNL0lc.png)

When are these values actually produced? Well, that's an interesting question - buffer only produces the pair at the point that it knows it actually has a pair. It needs to get the 2 values we asked for before it produces anything.