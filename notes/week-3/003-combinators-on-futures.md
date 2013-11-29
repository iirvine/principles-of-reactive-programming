#Combinators on Futures
Let's look at combinators on `Future`. We'll also see if `Future` is really a monad.... does it have a `flatMap`?

![img](http://i.imgur.com/0zbPu4l.png)

here's the standard scala library. There's our friend `flatMap`! So `Future` is a monad.

There's another supertype here - `Future` actually extends `Awaitable`. We'll talk more about that later. 

Next we're gonna look in a little more detail at the various operators on `Future`.

Remember that mess we arrived at last time, trying to use `onComplete` to chain together the `readFromMemory` method call with the `sendToEurope` call?

![img](http://i.imgur.com/WcI3koc.png)

`readFromMemory` returned a `Future[Array[Byte]]` - we registered the `onComplete` callback with that `Future`. If that was successfuly, we can grab that packet and send it to europe, and have that be be the `Future[Array[Byte]]` for the confirmation.

But we're stuck inside the `Success` case here! Now we're stuck inside spaghetti. 

Now just like with `Try[T]`, `flatMap` will lead us through the happy path!

```scala
val socket = Socket()
val packet: Future[Array[Byte]] = socket.readFromMemory()
val confirmation: Future[Array[Byte]] = packet.flatMap(p => { 
        socket.sendToEurope(p)
    })
```

*look how beautiful*

we can just `flatMap` our `Future[Array[Byte]]` that we get from `readFromMemory` - with that, we can get at our packet and send it to Europe. That gives us our `Future[Array[Byte]]` for `confirmation`. 

So again, we see that `flatMap` is the hero of every monad. It allows us to chain together computations inside a monad, an allow us to focus on the happy path. The monad will take care of all the noise. 

Let's drill down into a possible implementation of `sendPacketsToEurope`

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.imaginary.Http._

object Http {
    def apply(url: URL, req: Request): Future[Response] = 
        { ... runs the http request asynchronously ... }
}

def sendToEurope(url: URL, packet: Array[Byte]): Future[Array[Byte]] = 
    Http(url), Request(packet))
        .filter(response => response.isOK)
        .map(response => response.toByteArray)
```

Imagine that we've got an HTTP library, `scala.imaginary.Http`. The only thing that we want from our http library is that there's a factory method that takes a URL, a request, and then asynchronously runs this HTTP request and at some point gives us back a response. 

Of course, networks are unreliable. So this may still fail. This code is still not completely correct, because too many things can still go wrong. We want to turn this code into a robust mailsend program.

Let's refactor a bit - let's send our mail both to Europe *and* to the USA:

```scala
def sendToAndBackup(packet: Array[Byte]): Future[(Array[Byte], Array[Byte])] = {
    val europeConfirm = sendTo(mailServer.europe, packet)
    val usaConfirm = sendTo(mailServer.usa, packet)
    europeConfirm.zip(usaConfirm)
}
```

You might think "this is great! i'm sending it to europe, the USA, then we `zip` them together". Now we get a `Future[Pair]` - a `Future` that hopefully contains two confirmations.

Is that really safe? Of course not. If either one of them fails, the `Future` will fail - we have absolutely no guarantee that sending to both europe and the US will give us the success that we're after. If either one fails, the *whole thing fails*. This is actually a *worse* implementation, now the chance that something goes wrong is doubled.

Fortunately, the scala designers have already thought about this problem - in the library for `Future` there are these two functions:

```scala
def recover(f: PartialFunction[Throwable, T]): Future[T]
def recoverWith(f: PartialFunction[Throwable, Future[T]]): Future[T]
```

`recover` takes a partial function that takes a `Throwable` and returns a `T`. We can use that in case our `Future` has failed. We can check the `Throwable` and deliver a value that is returned when the computation is failed. It then returns a `Future[T]`. 

`recoverWith` is similar - instead of a `T`, the function it takes returns a `Future[T]` instead of a `T`, meaning we can do another async computation. If the first one failed, we can try another thing that takes a long time. 

So let's make an attempt to make `sendTo` safe for failures:

```scala
def sendTo(packet: Array[Byte]): Future[Array[Byte]] =
    sendTo(mailServer.europe, packet) recoverWith {
        case europeError => sendTo(mailServer.usa, packet) recover {
            case usaError => usaError.getMessage.toByteArray
        }
    }
```

So, again we take a packet and return a `Future[Array[Byte]]`. First, we're going to try to send our packet to europe - if that fails, we use `recoverWith` to send the mail to the US. We use `recoverWith` because we're trying something that also returns `Future[T]`. 

If sending the mail to the US fails, we've run out of possibilities - we'll just use the error message we got from that failure. 

Unfortunately, this method doesn't really do what we want - we want to send the packet to Europe, and sending it to the US was a backup strategy. What this thing returns in the case of failure is the error message we got trying to send the packet to the USA, which is not what we want - what we really want is the error message we got when we tried to send it to Europe. 

First of all, we want the right error message - we want the one for Europe, not the one for the USA. 

Also, our last code looked kinda ugly. Erik's not a fan of all this case matching business - we want to write code that looks better and does better recovery with less matching. 

We're going to define a new method on `Future[T]`, `fallbackTo`. It will take a call-by-name `Future` which we don't want to evaluate eagerly; we only want to evaluate if our first future fails. if our fallback future succeeds, we want that as the result, but if it fails we want to have the failure of our original `Future`.

let's assume we've already written this operator - what would `sendTo` look like?

```scala
def sendToSafe(packet: Array[Byte]): Future[Array[Byte]] =
    sendTo(mailServer.europe, packet) fallbackTo {
        sendTo(mailServer.usa, packet)
    } recover {
        case europeError => eropeError.getMessage.toByteArray
    }
```

Of course, we're still cheating - we haven't actually defined `fallbackTo`.

```scala
def fallbackTo(that: => Future[T]): Future[T] = {
    this recoverWith {
        case _ => that recoverWith { case _ => this }
    }
}
```

Neato. How does it work? Remember, `fallbackTo` was defined on `Future[T]`. we take a second `Future[T]`, a call by name parameter called `that`. What we want to return is a new `Future` - but we only want to call `that` when `this` fails. 

We already have part of that, because we've got `recoverWith`. If `this` succeeds, `recoverWith` will do nothing, exactly like we'd expect. If `this` returns a `Failure`, we'll run `that`. only at this point will we execute the fallback `Future`. If the fallback fails, we'll return `this`, which is exactly what we wanted!

Erik thinks this is super beautiful, and a little like poetry - `this recoverWith that recoverWith this`

###Awaitable
We're going to end this lesson by looking at the mysterious `Awaitable[T]` trait, the supertype of `Future[T]`. 

```scala
trait Awaitable[T] extends AnyRef {
    abstract def ready(atMost: Duration): Unit
    abstract def result(atMost: Duration): T
}
```

It has two methods, `ready` and `result`, that take a `Duration`. 

Erik cautions this trait should be used very carefully - it allows you to block on the result of a `Future`. Instead of calling `onComplete` or using the methods on `Future`, sometimes you want to go from `Future[T]` directly to `T`. 

That's what `Awaitable` allows you to do - `result` goes from a `Future[T]` into a `T`. It allows you to go out of the monad, and going out of a monad is always dangerous. 

There's a way to go *into* a monad, with operators like `flatMap` et al - they always return the monad value. But there's usually *no way to go out*. If you go out, you're doing something dangerous. Sometimes, we want to be dangerous. We want to be asynchronous where possible, but sometimes we just need to block. Erik wants to emphasize that you should *never* block unless it's absolutely necessary.

Let's see how we can use `result` to block on a `Future`. 

```scala
val socket = Socket()
val packet: Future[Array[Byte]] = socket.readFromMemory()
val confirmation: Future[Array[Byte]] = packet.flatMap(socket.sendTo(_))

val c = Await.result(confirmation, 2 seconds)
println(c.toText)
```

So, say we've grabbed packet from memory, sent it safely to europe and back, and we've gotten the confirmation. Now we want to print the text of the confirmation to STDOUT.

But, we have to wait until the `Future` has terminated - but how do we print a `Future`? We know how to print a string... so, we'll just call `Await.result`. In order to not block forever, there's a timeout we have to pass in. If the `Future` has not terminated within two seconds, this will throw. If it does terminate in that time, it'll give us the value - instead of a `Future[Array[Byte]]`, it'll be a `Array[Byte]`. 

What's the magic that allows us to pass in our duration by saying `2 seconds`? 

###`Duration`
```scala
import scala.language.postfixOps

object Duration {
    def apply(length: Long, unit: TimeUnit)
}

val fiveYears = 1826 minutes
```

`postfixOps` gives us this magical notation.