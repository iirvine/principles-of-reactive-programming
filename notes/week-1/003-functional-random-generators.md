##Functional Random Generators

We can use for-expressions for lotsa cool stuff - operations on sets, databases, or options.

Are for expressions tied to collections?

No! All that is required is some interpretation of `map`, `flatMap`, and `withFilter`

###Random Values

You know randoms....

```java
import java.util.Random
val rand = new Random
rand.nextInt()
```

Question: what's a systematic way to get random values for other domains - how would we get a random `boolean`, or a random `list` or `set`?

###Generators

Let's define a trait `Generator[T` that generates random values of type `T`. 

```scala
trait Generator[+T] {
    def generate: T
}
```

A random `int` generator would be easy enough - it'll just delegate to that java implementation of `Random`

```scala
val integers = new Generator[Int] {
    val rand = new java.util.Random
    def generate = rand.nextInt()
}
```

Once we have a generators for `int`, making one for `booleans` is easy - we can just generate a random number using the integers generator, and ask if it's greater than zero. If it is, we return true, if not, false.

```scala
val booleans = new Generator[Boolean] {
    def generate = integers.generate > 0
}
```

Pairs is also pretty easy: 

```scala
val pairs = new Generator[(Int, Int)] {
    def generate = (integers.generate, integers.generate)
}
```

All this works, but it's also a bit cumbersome - each time we have to set up a new anonymous class, define a generate function - it sucks. Can we do without all this boilerplate?

Ideally, we would like to write `val booleans = for (x <- integers) yield x > 0`

or for pairs: 

```scala
def pairs[T, U](t: Generator[T], u: Generator[U]) = for {
    x <- t
    y <- u
} yield (x, y)
```

Well, if we want to do that, what does the compiler expand it to?

`val booleans = integers.map (x => x > 0)`

```scala
def pairs[T,U](t: Generator[T], y: Generator[U]) =
    t.flatMap(x => y map (y => (x, y)))
```

So, we need `map` and `flatMap`!

###Generator with `map` and `flatMap`

```scala
trait Generator[+T] {
    self =>
    def generate: T

    def map[S](f: T => S): Generator[S] = new Generator[S] {
        def generate = f(self.generate) 
    }
}
```

`map` takes a function from the random value type `T` to a new random value of type `S` - it gives you a `Generator[S]`. It would generate new random values of type `T` using it's own generate method, then apply `f` to those random numbers, and those give you the random numbers on the `S` type.

There's a twist here on the call to `self.generate` - if we had written just `generate`, that would be `this.generate` - but the `this` in this new anonymous class would refer to the current method. It would be a recursive call to the generate method which would not terminate. What we need to do instead is call the generate method of the object one step further out. 

We do that by defining an alias for the `this` value of the outer generator, using the syntax `self =>`.

Next we need `flatMap`:

```scala
def flatMap[S](f: T => Generator[S]): Generator[S] = new Generator[S] {
    def generate = f(self.generate).generate
}
```

`flatMap` will give us a generator of `S` from the function `f` - it takes a random value to a whole sequence of random values. First we generate a random value of type `T`, using `self.generate` as before. We apply the function `f` to it, so that now we've got a complete generator on the new domain `S`. To pick a random value in that domain, we call `generate` again.

###Generator Examples

A useful, simple building block is the `single` generator, that always gives you back the same "random" value `T`

```scala
def single[T](x: T) Generator[T] = new Generator[T] {
    def generate = x
}
```

`choose` gives you an integer between the interval of `lo`  and `hi`

```scala
def choose(lo: Int, hi: Int): Generator[Int] = 
    for (x <- integers) yield lo + x % (hi - lo)
```

`oneOf` can pick an arbitrary value from a list of choices. You'd call it like `oneOf(red, blue, yellow)`. It takes a vararg argument `T*`, which means that we can give it as many choices as we want.

```scala
def oneOf[T](xs: T*): Generator[T] = 
    for (idx <- choose(0, xs.length)) yield xs(idx)
```

###A `list` Generator

a `list` is either an empty list or a non-empty list

```scala
def lists: Generator[List[Int]] = for {
    isEmpty <- booleans
    list <- if (isEmpty) emptyLists else nonEmptyLists
} yield list
```

First, we can flip a coin as to whether the list should be empty or non empty. Then, if the coin gave us the list should be empty, we return an empty list, with a `single` generator that always returns `Nil`

```scala
def emptyLists = single(Nil)
```

how would we get a generator for a non-empty list? 

```scala
def nonEmptyLists = for {
    head <- integers
    tail <- lists
} yield head :: tail
```
###Application: Random Testing

We know about unit tests - come up with some test inputs to program functions and a *postcondition*. 

The postcondition is a property of the expected result. We run the tests and verify that the program satisfies the postcondition.

Unfortunately, all we really know is that the program satisfies the postconditions on these test inputs - there might be others where the program still could fail. We'd need to be smart about finding good test inputs that excercise the program in all possible paths. 

Can we do it without the hassle of being smart? In some cases, yes, by generating random test inputs.

```scala
def test[T](g: Generator[T], numTimes : Int = 100)(test: T => Boolean): Unit = {
    for (i <- 0 until numTimes) {
        val value = g.generate
        assert(test(value), "test failed for " + value)
    }
    println("passed " + numTimes + " tests")
}
```