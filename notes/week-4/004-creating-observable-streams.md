#Creating Observable Streams
Now that we know all this cool stuff about Observables and their operators, let's look at how we could implement some of them ourselves!

###Observable
![img](http://i.imgur.com/qIZRP4a.png)

The main workhorse we'll be using is this overload on the `Observable` companion. It takes a function `Observer[T] => Subscription`. In that function, the observer can call `onNext` on the observer, and finally either `onComplete` or `onError`. 

The result of this application will be an `Observable[T]`. Erik calls this the mother of all factory methods - most of the rx operators can be created using this overload.

###Never
The first observable collection we're going to implement is a very, very simple one - `Observable.never`. This is an observable stream that will never give any notifications. It's completely silent.

```scala
def never() Observable[Nothing] = Observable[Nothing](observer => {
 Subscription {} 
})
```

###Error
The second collection we'll implement is `Observable.error`. The only thing this does is call `onError` on the observer.

```scala
def error[T](error: Throwable): Observable[T] =
    Observable[T](observer => {
        observer.onError(error)
        Subscription {}
    })
```

###startWith
The third operator we'll implement is `startWith`. It takes an Observable collection and prepends some extra elements in front.

![img](http://i.imgur.com/6OmfeXG.png)

We'll just use the factory method again.

```scala
def startWith(ss: T*) Observable[T] ={
    Observable[T](observer => {
        for(s <- ss) observer.onNext(s)
        subscribe(observer)
    })
}
```

It takes a varargs, `T*`, and returns an `Observable[T]`. We take the `observer` and push out all the values in the argument list to it. Once that's done, we just subscribe that same observer to our original stream, and that'll push out all the other values. 

###Filter
Let's look at a couple more realistic operators.

![img](http://i.imgur.com/2ETF5zF.png)

`filter` takes a predicate, and then throws away all other values that don't satisfy that predicate.

```scala
def filter(p: T=> Boolean): Observable[T] = {
    Observable[T](observer => {
        subscribe(
            (t: T) => { if p(t) observer.onNext(t) },
            (e: Throwable) => { observer.onError(e) },
            () => { observer.onCompleted() }
        )
    })
}
```

All we have to do is copy all the values from the input to the output that meet the predicate - if they don't, we just drop them on the floor. How do we do that?

Well, we call `Observable` and pass it this function that, given an `Observer`, will return a `Subscription`. How do we get a subscription? By subscribing to our source stream, and passing in the three functions for `onNext`, `onError`, and `onCompleted`. 

###map
![img](http://i.imgur.com/hQVZTPc.png)

We can't forget about `map`! 

```scala
def map[S](f: T => S): Observable[S] = {
    Observable[S](observer => {
        subscribe (
            (t: T) => { observer.onNext(f(t)) },
            (e: Throwable) => { observer.onError(e) },
            () => { observer.onCompleted() }
        )
    })
}
```

It's even simpler than `filter`. 

Here's some funsies that Erik suggests - compare the implementation of `map` on `Observable` to its implementation on `Iterable`:

```scala
def map[S](f: T => S): Iterable[S] = {
    new Iterable[S] {
        val it = this.iterator()
        def iterator: Iterator[S] = new Iterator[S] {
            def hasNext: Boolean = { it.hasNext }
            def next(): S = { f(it.next()) }
        }
    }
}
```

They're very similar, right? Unsurprising, because `Iterable` and `Observable` are dual!

###from
Time for a bigger challenge - lets try to implement a conversion to `Observable` from `Future`. 

![img](http://i.imgur.com/Hytjjys.png)

We want to take a `Future` and turn it into an `Observable` that only contains one value and then immediately calls `onComplete`. 

To get this done, first we'll have to take a little excursion and talk about `Subject`.

###Subjects and Promises
What are subjects?? well, they are to `Observable` as promises are to `Future`. Remember those? Wait, what are futures again.... where am i... 

```scala
def map[S](f: T => S)(implicit executor: ExecutionContext): Future[S] = {
    val p = Promise[S]()

    onComplete {
        case result => {... p.complete(E) ...}
    }(executor)

    p.future
}
```

Here's the implementation of `map` on `Future`. Remember? We created a new promise, waited for the completion of our `Future`, and then we applied the function and passed that to the `Promise` by calling `p.complete`. We returned `p.future`, the `Future` part of the `Promise`

![img](http://i.imgur.com/z6u9YWU.png)

We can visualize this as follows - we can `complete` a `Promise`, by sending it a `Try[T]`, and we can grab a `Future` from it. On that `Future` we can call `onComplete` by passing it a callback. Whenever we pass in the result for `complete`, the callback we register to the `Future` will be called.

###Subject[T]
![img](http://i.imgur.com/HydKI7L.png)

For `Observable`, we have something very similar - `Subject`. It has the same idea as a `Promise`, but the implementation is slightly different.

A `Subject` is both an `Observer` *and* an `Observable`. We can put a value into a `Subject`, by calling `onNext`, `onComplete`, or `onError` - that's the `Observer` part of it. 

The other thing we can do is *subscribe* to a `Subject`, with an `Observer` (or with three callbacks that implement the protocol). Then whenever we call `onNext`, `onComplete`, or `onError` on the `Subject`, it will be propogated to the callback functions, exactly how a `Promise` worked for `Future`! NEATO.

Remember the lecture about cold vs hot observables? A cold observable is one where we get a private source for each subscription - a  hot observable shares its source. Here we see that `Subject` makes a cold observable hot - it's a kind of sharing point where we can have multiple subscribers that listen to one source.

###Example - subjects are like channels
Let's look at some code! This shows that a `Subject` behaves like a channel, a fan-out point:

```scala
val channel = PublishSubject[Int]()

val a = channel.subscribe(x => println("a: " + x))
val b = channel.subscribe(x => println("b: " + x))

channel.onNext(42)
a.unsubscribe()

channel.onNext(4711)
channel.onComplete()

val c = channel.subscribe(x => println("c: " + x))
channel.onNext(13)
```

![img](http://i.imgur.com/VqPzkB7.png)

We're going to use a `PublishSubject[Int]`, called `channel`. We subscribe to that channel with two subscribers, `a` and `b`, that just print whatever value comes out.

We're going to push some values onto the channel - when we push 42 onto the channel, since both `a` and `b` are subscribed, we'll see that they'll both receive 42.

Next, `a` unsubscribes, so it will not be notified of any other values. If we call `onNext(4711)` , it's only received by `b`.

Finally, when we call `onCompleted()` on the channel, it's going to ignore all subsequent values - that value 13 is not propogated anywhere, because as soon as we call `onCompleted` on the channel, all the subscribers receive an `onCompleted`. Anything we send on the channel after we send an `onCompleted` will just be ignored. The RX contract guarantees that after we see an `onCompleted` or an `onError`, we'll never see any more values.

One interesting thing to note here - subscriber `c`, that subscribed to the channel after the `onCompleted` was fired, *still receives an `onCompleted`*. That way, `c` knows that it's subscribing to an already completed channel.

Let's look at another type of subject: the `ReplaySubject`

```scala
val channel = ReplaySubject[Int]()

val a = channel.subscribe(x => println("a: " + x))
val b = channel.subscribe(x => println("b: " + x))

channel.onNext(42)
a.unsubscribe()

channel.onNext(4711)
channel.onComplete()

val c = channel.subscribe(x => println("c: " + x))
channel.onNext(13)
```

![img](http://i.imgur.com/WwpFm4u.png)

A `ReplaySubject` buffers all the values it has seen in the past. 

for `a` and `b`, nothing's really different. They both receive the same values as before. 

But when we subscribe `c` to the already completed channel, guess what - since the channel has its history cached, when we subscribe with `c` it will replay the whole sequence - so `c` receives 42, 4711, and `onCompleted`. Again, if we push anything on the channel after it's been completed that value will be ignored and dropped on the floor. 

###Subjects Everywhere!
![img](http://i.imgur.com/xBJXtYm.png)

There are four different kinds of most common subjects in RX. 

The `PublishSubject` always sends out the current value. 

The `ReplaySubject` caches all the values.

The `BehaviorSubject` always caches the latest value. In the diagram, when the bottom subscriber subscribes, the latest value is 4711. So at the moment it subscribes, it gets 4711 plus all future values. 

The `AsyncSubject` caches the final value of the stream; the last `onNext` before an `onCompleted`. It's a little bit like a `ReplaySubject` - even if we subscribe to it after it has completed, it'll send out the cached value. 

###from

WHEW. Now that we know about subjects, let's finally see how we can convert a `Future` into an `Observable`!

```scala
object Observable {
    def apply[T](f: Future[T]): Observable[T] = {
        val subject = AsyncSubject[T]()
        f onComplete {
            case Failure(e) => { subject.onErorr(e) }
            case Success(c) => { subject.onNext(c); subject.onCompleted() }
        }
        subject
    }
}
```

We define a new constructor function in the `Observable` companion class. We're going to use the fact that an `AsyncSubject` behaves very much like a `Promise`. We wire up our `Future` to call the appropriate functions on our subject and WOW we're done! Now we return our subject and all is great and fine. When we subscribe to the observer part of the subject that comes out of here, it will always give us the value of the wrapped `Future`.

###Observable Notifications
`Future` takes in its callback a value of type `Try[T]`, which has two subtypes - `Success[T]`, and `Failure`. 

On the other hand, with `Observable`, we typically take three callbacks -`onNext`, `onError`, and `onCompleted`. In RX, there's a type that corresponds very closely to `Try[T]` - `Notification[T]`.

`Notification[T]` has three cases instead of two - guess what, they're exactly what you think they are. 

```scala
abstract class Notification[+T]
case class OnNext[T](elem: T) extends Notification[T]
case class OnError[T](t: Throwable) extends Notification[T]
case class OnCompleted[T] extends Notification[T]

def materialize: Observable[Notification[T]] = { ... }
```

that `materialize` method exists on `Observable`; it turns an `Observable[T]` into an `Observable[Notification[T]]`. That lets us do a pattern match on a `Notification[T]` much like we do with a `Try[T]` in the `onComplete` of a `Future`.

###Remember blocking?

```scala
val f: Future[String] future { ... }
val text: String = Await.result(f, 10 seconds)
```

![img](http://i.imgur.com/LOHdsev.png)

Remember when we were discussing `Future`, we said that sometimes we'd have to block - that's a bad practice, but sometimes we just need to block. To make this apparent in `Future`, we used this `Await` class to wait at most a certain duration. Our blocking was made very very explicit.

The same is true for `Observable` - sometimes, you just want to block, for example, if you're debugging. We use the `Observable.toBlockingObservable()` operator for that (or `BlockingObservable.from()`)

When we have a blocking observable, we get a few extra methods that are blocking - all the other methods in RX are nonblocking. 

![img](http://i.imgur.com/fh7Etoi.png)

###Converting Observables to scalar types
```scala
val xs: Observable[Long] = Observable.interval(1 second).take(5)
val ys: List[Long] = xs.toBlockingObservable.toList
```

Say we want a list out of our `Observable`, we first have to call `toBlockingObservable` on it. 

```scala
val zs: Observable[Long] = xs.sum
val s: Long = zs.toBlockingObservable.single
```

now say we call the `sum` operator on our original `xs` observable - since all the regular operators are nonblocking, the result of summing up all the values is still an `Observable[Long]`. However, it's an observable that has only a single value. When we want to get that value out of that observable, we can call `toBlockingObservable.single`. Note that `single` will throw if the source stream does not contain exactly one element. 

Recall from earlier lectures that a lot of the aggregate operators on lists, `sum`, `average` etc, can be defined in terms of `reduce`. 

![img](http://i.imgur.com/Q0cjd9Z.png)  

`reduce` just collapses all the values in the input stream into a single value. The thing in rx is that `reduce` still returns an `Observable` - it's an observable that has a single value. Verrry much like a `Future`.