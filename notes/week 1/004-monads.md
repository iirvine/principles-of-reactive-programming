##Monads!!

We've seen that datastructures with `map` and `flatMap` seem to be quite common. In fact there's a name that describes this class of data structures as well as some algebraic laws that they should have - they're called *monads*.

###Dafuqs a monad?

A monad is a parametric type `M[T]`, with two operations, `flatMap` and `unit`, that have to satisfy some laws.

```scala
trait M[T] {
    def flatMap[U](f: T => M[U]): M[U]
}

def unit[T](x: T): M[T]
```

Two functions - `flatMap`, which takes an arbitrary type `U` as a parameter, and a function that maps a `T` to a Monad of `U`, and it gives you back the same monad of `U`.

Also, there's `unit`, which takes an element of type `T`, and gives you an instance of the monad of type `T`.

in the literature, `flatMap` is more commonly called `bind`. We'll just stick with `flatMap`.

###Examples of Monads
* `List` is a monad, with `unit(x) = List(x)`
* `Set`, with `unit(x) = Set(x)`
* `Option`, with `unit(x) = Some(x)`
* `Generator`, with `unit(x) = single(x)`

`flatMap` is an operation on each of these types, whereas `unit` in scala is different for each monad; quite often it's just the name of the monad type applied to an argument, but sometimes, as in `Generator`s, we use `single(x)`

###Monads and map

`map` can be defined for every monad as a combination of `flatMap` and `unit`:

```scala
m map f == m flatMap (x => unit(f(x)))
        == m flatMap (f andThen unit)
```

a `map` applied to a monad with a function `f` would be `flatMap` of the application of `f` to the argument `x`, and then reinjecting the result into the monad using `unit`

We could also write this using the `andThen` combinator for function composition; first we apply the `f` function, and then we apply the `unit` function to the result of that.

In scala we don't have a `unit` we can call here - every monad gives different expression that gives the unit value. Therefore, in scala, `map` is a primitive function that is also defined on every monad.

###Monad Laws
To qualify as a monad, a type has to satisfy three laws that connect `flatMap` and `unit`:

* Associativity: as usual, a law about placing parentheses. We can either put them to the left or to the right:
```scala
(m flatMap f) flatMap g == m flatMap((x => f(x) flatMap g))
```

A domain where associativity is a bit easier to express is *monoids*, a simpler form of monads that doesn't bind anything. Integers are a monoid, and they're associative because `(x + y) + z` == `x + (y + z)`

* Left unit - this law says if we inject into the monad with `unit`, and then `flatMap` using `f`, the result is the same as simply applying `f` to the value of `x`:
```scala
unit(x) flatMap f == f(x)
```

* Right unit - this law says if we `flatMap` with the `unit` constructor, we end up with the same monad value as before.
```scala
m flatMap unit == m
```

###Checking monad laws

let's check these laws for `Option`. Let's look at it's `flatMap`

```scala
abstract class Option[+T] {
    def flatMap[U](f: T => Option[U]): Option[U] = this match {
        case Some(x) => f(x)
        case None => None
    }
}
```
`flatMap` should take an Optional value - if that optional value is `None`, we keep `None`. Else, we apply a given function to that value, and that will give us another optional value. 

###Checking the Left Unit Law

We need to show that `unit(x) flatMap f == f(x)` (ie, `Some(x) flatMap f == f(x)`). That's pretty easy. If we expand what `flatMap` means, we get a pattern-match that says, if it's `Some(x)`, apply `f` to it - therefore, `Some(x) flatMap f == f(x)`, and the first law holds.

```scala
Some(x) flatMap f 
== Some(x) match {
    case Some(x) => f(x)
    case None => None
}
== f(x)
```

###Checking the Right Unit law

we need to show that `m flatMap unit == m` (ie, `optValue flatMap Some == optValue`). again, we expand what `flatMap` means - it expands to a pattern match that says if it's `Some(x)`, return our function `f` applied to `x` - the function `f` here is `Some`, so we return `Some(x)`. That gets simplified to just `opt` - in each of the two branches of the pattern match we return exactly the thing we started with. So, the right unit law holds.

```scala
opt flatMap Some
== opt match {
    case Some(x) => Some(x)
    case None => None
}
== opt
```

###Checking the Associative Law
need to show that the sequence of the two `flatMaps` with the parentheses to the left is the the same thing as a `flatMap` of a `flatMap` with the parentheses to the right. eg, `(opt flatMap f) flatMap g == opt flatMap (x => f(x) flatMap g)`

Let's start with the left. `opt flatMap f flatMap g` expands to:

```scala
opt match { case Some(x) => f(x) case None => None }
    match { case Some(y) => g(y) case None => None }
```

now we take the second pattern match and move it inside the two branches of the first one - we know that the result of the first pattern match will be the selector of the second one. All we do take each branch of the first pattern match and make it the selector of the second.

```scala
opt match {
    case Some(x) => 
        f(x) match { case Some(y) => g(y) case None => None }
    case None =>
        None match { case Some(y) => g(y) case None => None }
}
```

We can simplify this expression. In the second case, if the optional value is `None`, the second pattern match is the only one that applies; if we get a `None`, we keep a `None`. thus we can simplify that case to:

```scala
opt match {
    case Some(x) => 
        f(x) match { case Some(y) => g(y) case None => None }
    case None => None
}
```

Now let's look at the first case. In that case, we say if we got a `Some(x)`, we match `f(x)` in turn. If we get `Some(y)`, we give you `g(y)`, and we keep a `None`. 

That's just `f(x) flatMap g`! And if we look at that expression in turn, it's also just another instance of a `flatMap` - and that's exactly the right hand side of our original equation that we wanted to prove. So `Option` is a monad!

```scala
opt match {
    case Some(x) => f(x) flatMap g
    case None => None
}
== opt flatMap (x => f(x) flatMap g)
```

### Significance of Laws for For-expressions

What do we care whether something passes the three monad laws?

One answer to that is that they give a justification for certain refactorings of for-expressions that are quite intuititive.

The associative law says essentially, we can inline nested for expressions. If we have a for expression like 

![img](http://i.imgur.com/x9MGMt4.png)

where the first generator is in turn a for expression, what we can do is essentially inline the two generators in one large for-expression. If the type in question satisfies the monad associativity law, result will always be the same.

![img](http://i.imgur.com/H5Q27GR.png)

The right unit law also has a significance for for-expressions - essentially, the for expression 

```scala
for (x <- m) yield x
```

where we generate `x` and immediately return it, is the same as the original value `m`.

The left unit law doesn't have an analogue for for-expressions.

###Another type: Try

Here's another type that may or may not be a monad - `Try`.

It resembles `Option`, but instead of `Some/None`, there is a success case with a value and a `Failure` case that contains an exception. 

```scala
abstract class Try[+T]
case class Success[T](x:T) extends Try[T]
case class Failure(ex: Exception) extends Try[Nothing]
```

the idea is that we'll use `Try` in cases where we want to propagate exceptions not up the call-stack, but say between different threads of different processes, or even between different computers. We want to bottle up an exception in a value we can freely pass around.

###Creating a Try

We can wrap an arbitrary computation up in a `Try` with `Try(expr)` - this gives a `Success(someValue)` or `Failure(someException)`

That's achieved by making `Try` an object in the standard library that has an `apply` method.

```scala
object Try {
    def apply[T](expr: => T): Try[T] = 
    try Success(expr)
    catch {
        case NonFatal(ex) => Failure(ex)
    }
}
```

What's important here is that the epxression is passed as a by-name parameter (the type `=> T`); otherwise, we would already have a value for `expr` by the time we got to our `try` block, so there wouldn't be a computation that could throw an exception.

###Composing Try

Just like with `Option`, we can compose Try-valued computations in for expressions

```scala
for {
    x <- computeX
    y <- computeY
} yield f(x, y)
```

If `computeX` and `computeY` succeed with results `Success(x)` and `Success(y)`, this will return `Success(f(x, y))`.

If either fails with an exception `ex`, this will return `Failure(ex)`, with the first exception that got thrown.

All we need to do to support this behavior is define `map` and `flatMap` on the `Try` type.

![img](http://i.imgur.com/cLNbto5.png)

so, `flatMap` takes a function from the domain `T`, that gives us a `Try[U]`. It will say, well, if we start out with a `Success(x)`, let's apply `f` to it. That'll give us the result value. If `f` throws an exception that is not fatal, then we'll package it up in a `Failure` value.

On the other hand if we started out with a `Failure` that gets propagated into the return.

`map` takes a simple function from `T => U`, and we have to wrap it up in a `Try`. We do this with a pattern match on success and failure. If we have a `Success(x)` we apply `f` to `x` and submit it to the `Try` constructor. Again, if `f` throws an exception we'll get a `Failure`, otherwise we'll wrap it up in a `Success`

So if we look at the relationship between `flatMap` and `map` - a `Try` value mapped with a function `f` is the same thing as a `flatMap` where the function `f` gets applied to `x` and the the result gets wrapped up in a `Try`.

```scala
t map f == t flatMap (x => Try(f(x)))
        == t flatMap (f andThen Try)
```

Is try a monad? 

No! The left unit law fails.

```scala
Try(expr) flatMap f != f(expr)
```

Indeed, the left-hand side will never raise a non-fatal exception - neither `Try` nor `flatMap` would. whereas the right-hand side will raise any exception thrown by `expr` or `f`. So the left unit law cannot possibly hold for `Try`.

`Try` in a sense trades one monad law for another law which is more useful in this context: *an expression composed from `Try`, `map`, `flatMap` will never throw a non-fatal exception*