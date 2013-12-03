#Promises

Let's fulfill our promise to talk about promises.

Here's an implementation of `filter` that doesn't use `await` - this is where we first discover a `Promise`:

```scala
def filter(pred: T => Boolean): Future[T] {
    val p = Promise[T]()

    this onComplete {
        case Failure(e) =>
            p.failure(e)
        case Success(x) =>
            if (!pred(x)) p.failure(new NoSuchElementException)
            else p.success(x)
    }

    p.future
}
```

So what's that `Promise` thing? Well, here's the trait:

```scala
trait Promise[T] {
    def future: Future[T]
    def complete(result: Try[T]): Unit
    def tryComplete(result: Try[T]): Boolean
}
```

It has a couple of methods, but the three most important ones are above. 

First of all, a `Promise` contains a `Future`. It also has two variations of complete methods, that take a `Try`. The only difference is that one returns `Unit` and one returns `Boolean`.

When we create a `Promise`, we can take a `Future` out of it - this `Future` will be notified whenever we call `complete` on the `Promise`. Think of a `Promise` as a mailbox - a mailman puts a value into the mailbox, puts up the flag (calls the callback), and then we get our mail delivered in the `onCompleted` callback. The value that we pass into `complete` in the `Promise` is the value that'll be passed into the `onCompleted` callback on the `Future`.

So, why are there two `complete` methods on the `Promise` trait? Remember that when we called `onCompleted` twice on the same `Future`, we said we'd always get the same value. *Once a `Future` is completed, it contains a single value* - whenever we call the callback, that's the value we get. This idempotency is reflected in the fact that we can only call `complete` once - we can only complete a `Promise` once. If we call `complete` on an already completed `Promise`, it'll throw. We can `tryComplete`, and it'll return `false` if the `Promise` has already been completed. 

Here's an example of trying to complete a single `Promise` twice:

```scala
import scala.concurrent.ExecutionContext.Implicits.global

def race[T](left: Future[T], right: Future[T]): Future[T] = {
    val p = Promise[T]()
    left onComplete { p.tryComplete(_) }
    right onComplete { p.tryComplete(_) }
    p.future
}
```

We're going to let two threads race to complete the `Promise` - if `left` or `right` completes, we're going to try to complete the `Promise`. Since the `onComplete` runs on the global context, the two threads will compete, and the first one that is able to complete the `Promise` will set the value of the `Future` returned.

Is this `race` operator useful at all? Well, suppose that one of the `Future`s is a computation we want to do, but we don't want to wait for it all the time - we can have a second computation that's like a timeout, that'll complete ahead of the other one so we're not waiting forever on the long computation. 

So let's look back and see if we can understand how `filter` is defined using `Promise`s. 

```scala
def filter(pred: T => Boolean): Future[T] {
    val p = Promise[T]()

    this onComplete {
        case Failure(e) =>
            p.failure(e)
        case Success(x) =>
            if (!pred(x)) p.failure(new NoSuchElementException)
            else p.success(x)
    }

    p.future
}
```

Remember that `filter` was defined on a `Future[T]`, and takes a predicate from `T => Boolean`. It has to return another `Future[T]`. 

The way we do it is that we allocate a `Promise` - we're going to complete it once the `Future` completes. We're going to install a callback on `this` that maps its completion onto the `Promise` - when `this` fails, our predicate will fail, so we'll just complete the `Promise` with a `Failure`. When `this` succeeds, we'll check that the predicate holds, and if it does, we'll complete the `Promise` with `Success`. Now that value will be propogated to the `p.future` we returned from this operator, so whoever's listening to this `Future` will receive the value. 
###Reimplementing zip
Remember that when we tried to make our `sendPacketToEurope` method robust using `zip`, erik dropped the bomb that this would not work - if either of the `Future`s would fail, the whole result of `zip` would fail. Is that actually true? Well, let's look at the implementation of `zip`:

```scala
def zip[S, R](that: Future[S], f: (T, S) => R): Future[R] = {
    val p = Promise[R]()

    this onComplete {
        case Failure(e) => p.failure(e)
        case Success(x) => that onComplete {
            case Failure(e) p.failure(e)
            case Success(y) p.success(f(x, y))
        }
    }
}
```

Just for funsies, we're using a slightly different signature than the `zip` in the standard library, which returns a `Future[(T, S)]`. Instead, we pass in a function that takes the result of the `Future`s `this` and `that`, and computes the `R`; the operator thus returns a `Future[R]`.

We can see from the implementation that if either of the `Future`s fail, the whole `Promise` fails. 

What erik doesn't like about it are all the case distinctions - let's see if we can remove that with `async`/`await`!

```scala
def zip[S, R](p: Future[S], f: (T, S) => R): Future[R] = async {
    f(await { this }, await { that })
}
```

Pretty simple.

###Sequence
We're going to implement a slightly more complicated operation on `Future[T]`. 

`sequence` takes a `List[Future[T]]` into a `Future[List[T]]`. We'll see this for many monads - if we have a `List[Try[T]]`, we can turn that into a `Try[List[T]]`, etc.

```scala
def sequence[T](fs: List[Future[T]]): Future[List[T]] = async {
    var _fs = fs
    val r = ListBuffer[T]()
    while (_fs != Nil) {
        r += await { _fs.head }
        _fs = _fs.tail
    }
    f.result
}
```