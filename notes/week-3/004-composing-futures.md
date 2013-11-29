#Composing Futures
In this lesson, we want to write code that uses `Future` in slightly more interesting ways - not just with `flatMap`. We want to write real programs that use computations with `Future`.

Remember our networking stack from before? 

```scala
val socket = Socket()
val packet: Future[Array[Byte]] = socket.readFromMemory()
val confirmation: Future[Array[Byte]] = packet.flatMap(p => { 
        socket.sendToEurope(p)
    })
```

![img](http://i.imgur.com/oPcVoK6.png)

```scala
val socket = Socket()
val confirmation: Future[Array[Byte]] = for {
    packet <- socket.readFromMemory()
    confirmation <- socket.sendToSafe(packet)
} yield confirmation
```

Now imagine that we want to do something slightly more complicated, something that involves control-flow maybe? In that case, both `flatMap` and for comprehensions are not the most convenient tools - if we want to be real functional programmers, we're gonna look at some *power tools*.

###Retrying to send
The challenge we're looking at is implementing the following method:

```scala
def retry(noTimes: Int)(block: => Future[T]): Future[T] = {
    ... retry successfully completing block at most noTimes
    ... give up after that
}
```

It takes an `Int`, and a lazy `Future[T]`, and returns a `Future[T]`. We want to retry this computation at most `n` times. As soon as it succeeds, we want to return it - otherwise we'll keep trying, up to `n` times. Since we want to re-run the `Future` every time, we have to pass it as a call by name parameter. If we didn't do that, the `Future` would be eagerly evaluated and always return the same result.

How do we do that? Well, here's a way using recursion:

```scala
def retry(noTimes: Int)(block: => Future[T]): Future[T] = {
    if (noTimes == 0) Future.failed(new Exception("Sorry"))
    else {
        block.fallbackTo {
            retry(noTimes - 1) { block }
        }
    }
}
```

We just recurse over the `noTimes` - if we have 0 times left, we've failed, so we return a failed `Future`. 

Otherwise, we use our friend `fallbackTo` - we execute the `Future`, and only if it fails, we call `retry` recursively. Notice, because of the laziness, everything works out. `fallbackTo` also takes a call by name parameter, so it only evaluates its argument if the `block` `Future` failed - now we call `retry` again with a call by name parameter.

![img](http://i.imgur.com/NjDdUj7.png)

Some *jerk* called Erik Meijer once said that recursion is the GOTO of functional programming. That guy Erik Meijer happens to be *this* Erik Meijer! So, he's not really allowed to write recursive programs. How can we write this without recursion?

###Folding Lists
As true functional programmers, we all know that instead of recursion, we should use some variation of `fold`. 

Here's a nice tip from Richard Bird, the real hero of list programming, to remember what `foldLeft` and `foldRight` means: 

![img](http://i.imgur.com/cW4UyNz.png)

If we look at the wind, and we say there's a 'northern wind', it means the wind comes from the north. This is the same with `foldLeft` and `foldRight` - the `foldRight` comes from the right:

![img](http://i.imgur.com/JUVE1fP.png)

Conversely, `foldLeft` comes from the left:

![img](http://i.imgur.com/yvRxHgC.png)

if we have `List(a,b,c)`, and we `foldRight(e)(f)` with a seed value `e` and a function `f`, what we're going to get will come from the right - we'll start with the seed value, then we take the last value of the list and apply the function: `f(c,e)` - then we take b, apply the function `f(b, f(c,e))`, and finally we apply `a` - `f(a, f(b, f(c, e)))`.

`foldLeft` starts from the other side, starting with the seed, and applying the function to each subsequent element.

So what we're going to try to do is write our `retry` function using `foldLeft` and `foldRight`.

Say that we want to do `retry(3) { futureBlock } `. What we want to do is *unfold* that into the following code:

```scala
((failed recoverWith block) recoverWith block) recoverWith block
```

this is a `foldLeft`! We are starting on the left and moving right. how do we do this?? 

```scala
def retry(noTimes: Int)(block: => Future[T]): Future[T] = {
    val ns: Iterator[Int] = (1 to noTimes).iterator
    val attempts: Iterator[Future[T]] = ns.map( _ => () => block)
    val failed = Future.failed(new Exception)

    attempts.foldLeft(failed)
        ((a, block) => a recoverWith { block() })
}
```

First, we need to create a list with `noTimes` copies of the block - we need to make sure that those `Future`s are not evaluated, so we create a list `(1 to noTimes)` and `map` the block over it. Now we have a nice list with copies of the block. 

Then we do our `foldLeft` - when we unfold this, it'll unfold into exactly what we're after. 

This isn't our best example of writing code - Erik says that if you're a real great programmer, you should write 'baby code'. We never want to write code where we have to think very very long to do something simple.

###Making effects implicit
Writing this retry with `foldLeft` or `foldRight` required us to think too long, where it should be really simple. One way to write 'baby code' is to go the opposite direction - instead of making effects explicit, we want to make effects *implicit*. Sometimes, that just makes life easier. 

We're going to see if we can make this effect of latency implicit - we want to take a function of type `T => Future[S]` and treat it as if really we had a function of type `T => Try[S]`, or even better, just plain old `T => S`. 

We're not going to *turn* that into a function of `T => S`, but we're going to *hide the effects* - we want to say yeah we know we're in a context where the effect is `Future`, but I don't want to deal with it. I want that to be implicit. 

Is this possible? uh huh!!

###Async away magic
To do this, we have to import some scala magic:

```scala
import scala.async.Async._
```

what we get is two functions: the `async[T]` function, which takes a body, a call by name block `=> T`, and an implicit `ExecutionContext` and returns a `Future[T]`:

```scala
def async[T](body: => T)(implicit context: ExecutionContext): Future[T]
```

Think of this as just another factory method for `Future`. It's very similar to the basic `Future` factory function. The big difference is that for `async` is that if we're *inside* and async block, we can use the `await` function that turns a `Future[T]` into a `T`. 

We saw before that when we did that we were blocking, but in this case, it does that *without* blocking - that's the magic of the `async`/`await`. There's a magic compiler transformation that allows us to wait without blocking, to treat a `Future` as a regular value without blocking.

The great thing is that we can now write regular control flow inside our `async` block, allowing us to write super, super natural baby code.

###Small print
Since the `async` operator relies on compiler magic, there are some restrictions, the main one being that we *cannot use await under a try/catch* - if a `Future` can fail, we have to use our `Try` constructor that takes a `Future[T]` into a `Try[T]` to make sure the exceptions are materialized. There are some more:

![img](http://i.imgur.com/ddocnPt.png)

Let's retry `retry`!

```scala
def retry(noTimes: Int)(block: => Future[T]): Future[T] = async {
    var i = 0
    var result: Try[T] = Failure(new Exception("sorry man!"))
    while (i < noTimes && result.isFailure) {
        result = await { Try(block) }
        i += 1
    }
    result.get
}
```

We enter an `async` block, and inside there we can just write regular control flow. We'll just do it like any normal programmer - we declare a variable, we start with the result to be failure, since we might not be able to execute the block without failing, and then we do a simple loop. As long as we have not gotten a success, and we still have tries left, we'll `await` the result of the block, and increase the counter and loop. 

Remember the small print - we can't `await` with a try/catch, so we have to materialize the exceptions in our `Future` by using the constructor function that took a `Future[T]` and returns a `Future[Try[T]]` that we did a few lessons ago.

This is baby code. Everyone can understand what it does. And even though it uses mutable state on the inside, on the *outside* it's purely functional. 

Let's write some other examples using `await`!

```scala
def filter(p: T=> Boolean): Future[T] = async {
    val x = await { this }
    if (!p(x)) throw new NoSuchElementException()
    else x
}
```

Here's how we'd write `filter`: it has to return a `Future`. Whenever we have a function that needs to return a `Future`, think about if we can use `async`. It's an extremely powerful way to create `Future`. 

This function is defined on `Future[T]` - we need to apply a predicate from `T => Boolean`. So, we need to go from `Future[T]` to `T`, yet again! How do we do that?? Well, we'll use `await`, dummy!

We'll just await ourselves: `await { this }`, to give `x` the type `T`. Then, we check the predicate - if it hold we return the value, else we throw an exception, which is how we represent an empty `Future`.