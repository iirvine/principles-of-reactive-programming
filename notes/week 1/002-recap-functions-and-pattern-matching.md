###Recap - case classes.

Case classes are scala's prefered way to define complex data.

Here's some JSON:

![img](http://i.imgur.com/Lfr6gDK.png)

How would we represent JSON data in scala? Well, the most straightforward way would be to have a set of case classes that capture the different cases of a JSON object.

![img](http://i.imgur.com/0AJLIaw.png)

Here's that JSON from before, but with our scala representation:

![img](http://i.imgur.com/lQsnEZV.png)

One could dress this up further and make the construction even shorter, but this is the basic idea.

###Pattern Matching

So, let's do something with this data - let's print it as a string.

![img](http://i.imgur.com/4Vc0scP.png)

Pretty self explanatory, except maybe JObj...

If we have a json object, it'll have a map, here called `bindings` - we have to traverse that map, and for each binding we find, we'll have a pair from `(key, value)`. We then have a pattern match directly on the `map` function, that says well, apply this function that takes a key and a value and returns the key in quotes, a colon, and the value subjected to the show function.

That would give you a list of strings which contain `key:value`. We take that list, concatenate all the elements with commas, put it in braces, and we're done.

Let's take a closer look at `JObj` matcher. We have a `case` function that's passed directly to the `map` combinator - what's the type of this function?

###Case Blocks

Specifically, what's the type of a function like

```scala
{ case (key, value) => key + ":" + value }
```

As it is, the scala compiler would flag this as an error - it would say that an anonymous function lacks an expected type for the parameters; we'd have to give this function a type from the outside. 

If we plug this function into `map` from before, we'll find that `map` wants a function type of `JBinding => String`

###Functions are Objects

In scala, every concrete type is the type of some class or trait; the function type is no exception. A type like `Jbinding => String` is just shorthand for `scala.Function1[JBinding, String]` - where `scala.Function1` is a trait and `JBinding` and `String` are its type arguments.

###The Function1 Trait

Here's an outline of trait `Function1`:

```scala
trait Function1[-A, +R] {
    def apply(x: A): R
}
```

It's a trait with two type parameters, and an abstract function called `apply` which takes an argument of type `A` and gives you a result of type `R`. We're gonna gloss over the variance of our type parameters; let's look at the pattern matching block again. What would it expand to? 

If the type is `Function1`, what would the actual value be? Well, it'd be a new instance of `Function1` - we'd need to give a definition of the `apply` function. 

```scala
new Function1[JBinding, String] {
    def apply(x: JBinding) = x match {
        case (key, value) => key + ":" + show(value)
    }
}
```

###Subclassing Functions
One nice aspect of functions being traits is that we can subclass the function type - for instance, maps are functions from keys to values:

```scala
trait Map[Key, Value] extends (Key => Value)...
```

Sequences are functions from indices of `Int` to values. That's why we can write `elems(i)` for sequence (and array) indexing. Sequence indexing is written the same way as function application because sequences are functions.

###Partial Matches
We've seen that a pattern matching block like 

```scala
{ case "ping" => "pong" }
```

can be given type `String => String`.

```scala
val f: String => String = { case "ping" => "pong" }
```

What would happen if we tried to use our `f` function for a case that we haven't defined, like `f("abc")`? We'd get an error! WTF.

It would be nice to find out, given a function `f`, whether a function is applicable to a given argument. With the function type itself, we can't do that, but it turns out there's another way....

###Partial Functions
```scala
val f: PartialFunction[String, String] = { case "ping" => "pong" }
f.isDefinedAt("ping") //true
f.isDefinedAt("pong") //false
```

`PartialFunction` is another subtype of function; like function, we can apply it to an argument, but we can also query whether the function is defined for a given argument, with `f.isDefinedAt(argument)`