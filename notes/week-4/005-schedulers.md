#Schedulers

How would we implement `from`, an operator that takes an iterable of values and turns it into an Observable? 

![img](http://i.imgur.com/6g4hFjk.png)

We want to pull each value out of the iterable, and push it into the Observable. The real challenge is that we don't want to do this only for a finite sequence - we also want this to work for an infinite sequence.

```scala
def from[T](seq: Iterable[T]: Observable[T] = {...})

val infinte: Iterable[Int] = nats()

val subscription = from(infinite)
    .subscribe(x => println(x))

subscription.unsubscribe()
```

we'll define our infinite sequence of natural numbers using an anonymous class, like so:

```scala
def nats(): Iterable[Int] = new Iterable[Int] {
    var i = -1
    def iterator: Iterator[Int] = new Iterator[Int] {
        def hasNext: Boolean = {true}
        def next(): Int = { i +=1; i }
    }
}
```

To create our observable, we're going to use our factory method `create` again.

here's a first attempt to define `from`:

```scala
def from[T](seq: Iterable[T]): Observable[T] = {
    Observable(observer => 
        seq.foreach(s => observer.onNext(s))
        observer.onCompleted()

        Subscription{}
    )
}

val infinte: Iterable[Int] = nats()

val subscription = from(infinite)
    .subscribe(x => println(x))

subscription.unsubscribe()
```

What happens when we use our operator on that infinite stream of numbers? Guess what, we'll never reach our call to `unsubscribe()`. If the stream is infinite, `from` will not terminate, and we'll never get back our `Subscription`. 

![img](http://i.imgur.com/RNU3tKb.png)

What should we do? We should introduce some concurrency up in here! We'll do the iteration on a seperate thread - that calls `onNext` on the observer such that the consumer can call `unsubscribe` on its own thread. Then we won't be stuck!

Remember how every `Future` took an implicit `ExecutionContext` on which the work for the future was run?

```scala
object Future {
    def apply[T](body => T)(implicit executor: ExecutionContext): Future[T]
}
```

For Observables, we have a similar concept, except that instead of an `ExecutionContext`, we have a `Scheduler`, since `Future` only returns one result, and Observables return multiple.

###Schedulers
```scala
trait ExecutionContext {
    def execute(runnable: Runnable): Unit
}
```

The `ExecutionContext` for `Future` has a single method, `execute` that takes a `Runnable`. The reason it takes `Runnable` is for legacy reasons - it's an existing java type that really corresponds to a block that returns `Unit`. In rx we can do the more modern thing and just take a regular block.

The other difference - in a `Future`, it's impossible to cancel the work. Once you execute a `Runnable`, there's no way to cancel it. for rx we want to be able to unsubscribe, so instead of returning `Unit`, we'll return a `Subscription`

```scala
trait Scheduler {
    def schedule(work: => Unit): Subscription
}
```

here's a small example of using a scheduler:

```scala
val scheduler = Scheduler.NewThreadScheduler
val subscription = scheduler.schedule {
    println("hello world")
}
```

When we schedule work on a `NewThreadScheduler`, it'll take our block and schedule it on another thread and run it there. Very simple!

Let's try to define `from` using schedulers!

```scala
def from[T](seq: Iterable[T])(implicit scheduler: Scheduler): Observable[T] = {
    Observable[T](observer => {
        scheduler.schedule {
            seq.foreach(s => observer.onNext(s))
            observer.onCompleted()
        }
    })
}
```

Great, looks good. We've scheduled the `foreach` on a new thread. When that terminates, we call `onCompleted`. Does this work?

![img](http://i.imgur.com/G4DV7HV.png)

GOD DAMMIT ERIK.

When we schedule work on a scheduler, it can only be unsubscribed while the work has not yet started... once we start `foreach`ing over our collection, there's typically *no way* we can cancel that work. We only have a very brief moment in time when we can cancel this, making this not a very useful implementation.

WHAT CAN WE DO??

![img](http://i.imgur.com/y82sysS.png)

Ooooooh, right.

For a second attempt, let's look at a few other methods in the `Schedulder` trait. It has a second method that looks like this:

```scala
def schedule(work: Scheduler => Subscription): Subscription
```

It gets a *`Scheduler`* as an argument... this function can then use that scheduler to schedule multiple steps! It then returns a `Subscription` to the caller that they can use to cancel these steps. 

We can do better! We'll take that function, and implement a third overload:

```scala
def schedule(work: (=> Unit) => Unit): Subscription
```

It has a bizarre looking type `(=> Unit) => Unit`. The heck is that? Let's look at our new improved implementation of `from`:

```scala
def from[T](seq: Iterable[T])(implicit scheduler: Scheduler): Observable[T] = {
    Observable[T](observer => {
        val it = seq.iterator()
        scheduler.schedule(self => {
            if (it.hasNext) { observer.onNext(it.next()); self() }
            else { observer.onCompleted() }
        })
    })
}
```

Here's the deal: we're unfolding the loop from the last attempt and just doing it ourselves. We grab the `Iterator` from the iterable, and then we schedule the following work: 

```scala
scheduler.schedule(self => {
    if (it.hasNext) { observer.onNext(it.next()); self() }
    else { observer.onCompleted() }
})
```

We look if there's still a value, in which case we call `onNext` - and then, we *reschedule ourself recursively*.

Here's how this looks in a picture:

![img](http://i.imgur.com/xh5FWj8.png)

Let's run our test!

```scala
val infinte: Iterable[Int] = nats()

val subscription = from(infinite)
    .subscribe(x => println(x))

subscription.unsubscribe()
```

![img](http://i.imgur.com/9J3E8Xk.png)

It works! Wooooo! `from` now schedules itself recursively - at each schedule, there's a chance for our caller to unsubscribe. 

We're all curious about how this recursive scheduler works... Well, here's the implementation:

![img](http://i.imgur.com/HOIMogQ.png)

It takes `work` that takes a continuation that returns `Unit`. As always, it returns a `Subscription`. 

How're we going to make this work? If we're going to take multiple steps, then at every step we're going to want to be able to unsubscribe. We'll use our good friend `MultipleAssignmentSubscription` to create a `Subscription` that we pass to the caller that it can use to unsubscribe.

What we do then is schedule work and then *update* the multiple assignment subscription everytime we reschedule work. 

![img](http://i.imgur.com/As1OkhP.png)

So we have a `Scheduler` (the red bubble). Every time the work gets scheduled to run, it does some work, and then reschedules itself. When it does that, it updates the multiple assignment subscription to point to the current work - so when we unsubscribe, it cancels the current work.

So why did we do this weird `loop` definition with three parameters? We all know we could do a local definition in the body of `schedule` itself such that we can capture the parameters in the closure instead of passing them explicitly. We're functional programming ninjas so let's try that out.

![img](http://i.imgur.com/5biuQ3c.png)

###Convert Scheduler to Observable[Unit]

This crazy recursive scheduling business is all nice and well, but as functional programmers we know we want to capture patterns we often use as a higher order function - so let's do that here. 

We'll use this crazy recursive scheduler to define a new constructor function for `Observable` that'll just return an infinte stream of `Unit` by recursively scheduling itself. 

WHAT.

```scala
object Observable {
    def apply()(implicit scheduler: Scheduler): Observable[Unit] = {
        Observable(observer => {
            scheduler.schedule(self => {
                observer.onNext(())
                self()
            })
        })
    }
}
```

First we have to call `onNext()`, and then we call `self()`, which schedules itself, etc etc.

To understand how ticks work, let's revisit `Observable.apply`. We're going to get a little bit of an algebraic law we can use to reason about it.

```scala
object Observable {
    def apply(s: Observer[T] => Subscription) = new Observable[T] {
        def subscribe(o: Observer[T]): Subscription = { *Magic*(s(0)) }
    }
}
```

What the `apply` method does conceptually is just defines the `subscribe` method by calling the function we passed in (which has exactly the same arguent as the `subscribe` function, ie, it takes an `Observer[T] => Subscription`); then, it calls our function here with the Observer, `o`. Now we get here a `Subscription`. 

But that's not all! There's also some *magic* going on. We'll talk about the *magic* later.

Given this conceptually, we get the following rewrite rule: 

```scala
val s = Observable(o => F(o)).subscribe(observer) 
= *conceptually*
val s = *Magic*(F(observer))
```

IE, when we subscribe to an `Observable` that we create using the function we just defined, that is really the same as calling the function directly with the Observer (with a little *magic* around it). This is the model we'll use to understand our ticks function.

One of the magical things `Observable.create` does for us is auto-unsubscribe - what does that mean? 

It means that when we create an Observable by passing some function `F`, as above, when that function calls `onCompleted` or `onError` on the Observer, the magic will automatically call `unsubscribe` on the subscription. That guarantees that either of those functions are only called once; any subsequent `onNext` calls on the observer will do nothing.

The main reason we have all this magic is to enforce the RX Contract - a stream in RX always has the following shape: zero or more `onNext` calls, followed by *exactly* one `onCompleted` or `onError`. That can be optional in the case of an infinite stream that never calls either of those functions.