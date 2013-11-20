##Functions and State

So far we've concentrated on *pure* functional programming - we've only worked with pure functions and immutable data. 

In a reactive system of any size, sooner or later we'll have state that needs to change or be maintained, or some state changes that need to be signaled and propagated.

To express this we're going to broaden our notion of functions to work with mutable state. OMG.

Our programs up till now have been side-effect free - therefore, the concept of *time* hasn't really been important. For all programs that terminate, any sequence of actions would have given the same results. This is also reflected in the substitution model of computation

###Reminder: The Substitution Model

Programs can be evaluated simply by *rewriting the program text*.

The most important rule covers functions applications. Here it is real quick:

Say we have a function definition `f` with parameters `x1, ..., xn` , and a body `B`. Then later on we have a call to the same function `f` with the function values `v1, ..., vn`. The substitution model says that the program can be rewritten by keeping the application and all the other program elements, but replacing the call with the body of the function `B`, where all the formal parameters (`x1,...,xn`) are replaced by the actual values (`v1,...,vn`)

###Rewriting Example

Say we've got the following two functions `iterate` and `square`

```scala
def iterate(n: Int, f: Int => Int, x: Int) =
    if (n == 0) x else iterate(n-1, f, f(x))
def square(x: Int) - x * x
```

Iterate would apply the given function `f` `n` times on the given int `x`. 

The call `iterate(1, square, 3)` gets rewritten as follows: 

`-> if (1 == 0) 3 else iterate(1-1, square, square(3))`

First we take the right hand side of `iterate` and we replace the actual arguments for the formal parameters `n`, `f`, and `x`. 

`-> iterate(0, square, square(3)`

Next, we do two auxiliary reductions, simplifying the arithematic expression `1 = 0` to `false`, and then the next reduction would immediately simplify the if then else to take the `else` part.

`-> iterate(0, square, 3 * 3)`

Next we have to rewrite the call of `square(3)` - expanding the right hand side of `square` gives us `3 * 3`

`-> iterate(0, square, 9)`

Arithmetic simplification gives us the next line.

`-> if (0 == 0) 9 else iterate(0 - 1, square, square(9))`

Next we have another expansion of `iterate`. 

`9`

Some subsequent reductions would give us the final result!

###Observation
Rewriting can be done anywhere in a term, and all rewritings that terminate lead to the same solution.

This is an important result of the lambda calculus, the theory behind functional programming.

The first reduction was this line here:

```scala
-> if (1 == 0) 3 else iterate(1-1, square, square(3))
```

We rewrote it to this expression here:

```scala
iterate(0, square, square(3))
```

That's not the only thing we could do with this expression. Alternatively, we could also have concentrated on the nested call of `square(3)`, and rewritten that one:

```scala
if (1 == 0) 3
else iterate(1 - 1, square, 3 * 3)
```

now we have two different terms that the same term can rewrite to, but the important result here is that it *doesn't matter* which of the two we pick, because the end result is the same - both of these terms will give the same answer.

The idea that we can rewrite anywhere in a term but all results yield the same result (sometimes called *confluence*) is called the Church-Rosser Theorem of lambda calculus. 

All these observations hold in the world of pure functional programming.... what happens when we throw state into the mix?

###Stateful Objects
We normally describe the world as a set of objects, some of which have state that *changes* over the course of time.

Wait... what does that mean. Well, an abstract but accurate definition is that an object has a state if its behavior is influenced by its history.

If we take a bank account as an example, it has state because the answer to the question "can I withdraw 100 CHF" would depend on the previous history of the account. 

###Implementation of State
Every form of mutable state is constructed from some variables.

A variable definition in scala is written like a value definition, but with the keyword `var` instead of `val`

```scala
var x: String = "abc"
var count = 111
```

Just like a value definition, a variable definition associates a value with a name. 

However, in the case of variable definitions, this association can be changed later through an assignment, like in java:

```scala
x = "hi"
count = count + 1
```

###State in Objects
In practice, objects with state are usually represented by objects that have some variable members.

```scala
class BankAccount {
    private var balance = 0
    def deposit(amount: Int): Unit = {
        if (amount > 0) balance = balance + amount
    }
    def withdraw(amount: Int): Int = {
        if (0 < amount && amount <= balance) {
            balance = balance - amount
            balance
        } else throw new Error("insufficient funds")
    }
}
```
Our `BankAccount` class declares one private variable, and two methods that can be used to change that variable through assignments.

To create bank accounts, we use the usual notation for object creation:

```scala
val account = new BankAccount
```

###Statefulness and Variables
We've seen statefulness is often connected to having variables... let's see how strong that connection is in a couple examples. Remember the definition of streams from #progfun (I haven't watched that week yet, oops). Instead of using a lazy val, we could also implement non-empty streams using a mutable variable:

```scala
def cons[T](hd: T, tl: => Stream[T]) = new Stream[T] {
    def head = hd
    private var tlOpt = Option[Stream[T]] = None
    def tail: T = tlOpt match {
        case Some(x) => x
        case None => tlOpt = Some(tl); tail
    }
}
```

The idea is that we have a function `cons` that creates a stream, consisting of a head `T`, and a computation tail that gives you the rest of the stream when it's demanded.

The `cons` function creates a new anonymous class of type `Stream[T]`; it has a mutable variable `tlOpt` of type `Option[Stream[T]]`, initialized to `None`. The `tail` operation on `cons` would query the `tlOpt` - if it already has some value `x`, that value is the tail. If it's still set to `None`, the `tlOpt` will be computed by calling `tl` and wrapping it in a `Some` and returning the result.

Here's the question - is the result of `cons` a stateful object? Well, yes and no, depending on the assumptions you make.

One common assumption is that streams should only be defined over purely functional computations; so the `tl` operation here should not have a side-effect. 

In that case, the optimization to cache the first value of `tl` in `tlOpt` and reuse it on all previous calls to `tl` is purely an optimization that avoids computations, but doesn't have an observable effect outside the class of `Stream`. So, the answer would be that streams are *not* stateful objects.

On the other hand, if you allow side effecting computations for `tl` (say, `tl` has a `println` statement), then we would see that the second time `tl` is called on the stream, it would come straight out of the cache, so there would be no side effect performed. Whereas, the first time it's called, the operation would be performed *including* the `println` statement.

That means the operation `tl` depends on the previous history of the object; it would be different depending on whether a previous `tl` was performed or not. In that case, `cons` *is* a stateful object, provided that you also allow imperative side-effecting computations for `tl`.