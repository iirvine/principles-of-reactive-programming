#Monads and Effects
Erik's here! 

We're gonna be talking a lot about futures, promises, observables, and observers.

Also - monads!!!

Erik's a bit more loosey goosey with monads - he uses the word in the informal sense, meaning a type that has `map` `flatMap` and `unit`, and is really used to make the return type of a function more expressive. In fact, he will *never* worry about the monad laws. 

Recall that `Try[T]` has the *shape* of a monad, but it doesn't satisfy all the laws - regardless, Erik's gonna call that shit a monad. That's just how he rolls. 

###Four essential effects in programming
![img](http://i.imgur.com/6iGcmK9.png)

Before we can talk about futures and observables, which are about asynchronous computation, first we want to revisit `Try` and `Iterable`, which are about synchronous computation.

Let's start with `Try`.

###A simple adventure game
Here's a game:

```scala
trait Adventure {
    def collectCoins(): List[Coin]
    def buyTreasure(coins: List[Coin]): Treasure
}

val adventure = Adventure()
val coins = adventure.collectCoins()
val treasure = adventure.buyTreasure(coins)
```

Not as simple as we might think - things can fail. We might get eaten by a grue.

How is `collectCoins` implemented?

```scala
def collectCoins(): List[Coin] = {
    if (eantenByMonster(this))
        throw new GameOverException("Oops")
    List(Gold, Gold, Silver)
}
```

If we get eaten by a monster, we throw an exception - otherwise we get back some phat loot.

The code that we wrote before doesn't know anything about the fact that an exception might be thrown - it just sees that it returns a list of coins. When we call the method, there's no indication that it might fail. 

```scala
def buyTreasure(coins: List[Coin]): Treasure = {
    if (coins.sumBy(_.value) < treasureCost)
        throw new GameOverException("Nice try!")
    Diamond
}
```

`buyTreasure` can also fail - if we don't have enough coins, we don't get any treasure! 

Now we have two implementations of methods that can both fail. Something *magic* is happening in between these calls.

###Sequential composition of actions that may fail
What's really happening: after we call `adventure.collectCoins()`, we're blocking until we have collected all the coins - we only continue if there was no exception. Similarly with `buyTreasure` - we block until the treasure is bought.

Our code only shows the happy path - it doesn't show what happens when there are exceptions. 

###Expose possibility of failure in the types
Instead of having a function from `T => S`, what we want is to make the fact that the function can throw explicit in its type. 

The way we do that is using the standard scala type `Try[T]` - so we want a function type of `T => Try[S]`

Let's look at how `Try` is defined:

![img](http://i.imgur.com/WqoEX5y.png)

`Try` is defined as a type with a case for `Success`, in which case we just have the `elem` of type `T`. The other possibility is a `Failure`, in which case we have a `Throwable`.

If we use `Try` to make the chance of exceptions explicit, the signature of `collectCoins` changes - instead of returning a `List[Coin]`, we now return a `Try[List[Coin]]`.

What does that mean?

It means we can either return `Failure`, or `Success[T]`, which is our coins!

###Dealing With Failure
When we're dealing with failure explicitly, our code gets a little more ugly:

```scala
val adventure = Adventure()
val coins: Try[List[Coin]] = adventure.collectCoins()

val treasure: Try[Treasure] = coins.match {
    case Success(cs) => adventure.buyTreasure(cs)
    case Failure(t) => failure
}
```

Now we can't just call `buyTreasure` any more - it expects a `List[Coin]`. Our coins are all wrapped up in a `Try`! Now we have to pattern match on our coins and if it is a success, we pass it into the method.

###Working with `Try`
To make our code look nicer, we're going to use some of the higher-order functions that are defined on the `Try` type.

![img](http://i.imgur.com/rSnjL0f.png)

Let's checkout our old friend `flatMap` - it takes a `Try[T]`, and a function that takes a `T` and returns a `Try[S]`. It then returns a `Try[S]` - this exactly what we were looking for!

We collected our coins, which returned a `Try[List[Coin]]`; with `flatMap`, we can get directly at the `List[Coins]`, and `flatMap` will take care of propagating all the errors for us.

The fact that `Try[T]` has a `flatMap` operator means that it is a *monad*. It's a monad that handles exceptions. 

What's a monad? It's a type with operators that *guide you through the happy path*. The nice thing about a monad is that the effects are visible in the type. 

We don't want to deal with failure all the time - we want to *see* that things can fail, but we want to automate handling of that failure. That's what monads give you.

###Noise reduction
`flatMap` reduces some of the noise of our code - our ugly pattern matched code from before can just become:

```scala
val adventure = Adventure()

val treasure: Try[Treasure] = 
    adventure.collectCoins().flatMap(coins => {
        adventure.buyTreasure(coins)
    })
```

`adventure.collectCoins()` is of type `Try[List[Coin]]` - but with `flatMap`, we get access to our list of coins! We can then use it to buy treasure - tasty, tasty treasure. Any exceptions are automatically propagated by the implementation of `flatMap`.

###Using Comprehension syntax
`flatMap` also lets us use `for` comprehensions:

```scala
val treasure: Try[Treasure] = for {
    coins <- adventure.collectCoins()
    treasure <- buyTreasure(coins)
} yield treasure
```

One way to remember how these comprehensions work - on the right hand side of the arrow, we have something of type `Try[T]`, and on the left, we have something of type `T`. The arrow takes the value of type `T` *out* of the monad; it reduces the type. 

Let's look at how some of the operators on `Try[T]` are defined:

![img](http://i.imgur.com/d18LUSz.png)

our constructor function takes a block that returns a `T` and it turns that into a `Try[T]`. When we want to do a `Try[T]`, we want to have a computation that you can pass in, in order to do the try catch at the root of `Try` - thus, it's essential that we have our `r` parameter be call by name.

We take that block and we try to execute it - if it succeeds, we just wrap it in our `Success` constructor. If it fails, we wrap the thrown exception in the failure constructor. 

Once we have the factory method, we can define `map`. We match on `this`, which is of type `Try[T]`. If `this` is of type `Success(value)`, we were successful! So, we can try to call `f` with the value - but we now have to use the `Try` constructor to make sure that if `f(value)` throws, we wrap it in a `Failure`, and if it succeeds, we wrap it with `Success`. If we failed, we just propagate the failure.

One way to look at `Try` is that it *materializes exceptions* - our basic type `T` doesn't say anything about its exceptions, but the `Try[T]` makes all exceptions explicit. They're turned from something that happens in the control flow of our program into an actual *data value* that we can pattern match on with `map`.

Here's the definition of `flatMap` for `Try[T]`:

![img](http://i.imgur.com/7u5ZiOj.png)