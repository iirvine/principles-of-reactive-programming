#Identity and Change

Mutable state and changing objects has repurcussions on object identity - when are two objects the same, and when are they different?

Previously the question of whether two objects were the same had an easy answer; if we had two value definitions, `val x = E; val y =E`, where `E` is an arbitrary expression, then it is reasonalbe to assume that `x` and `y` are the same. We could have also written `val x = E; val y = x`

We call this property *referential transparency*.

But once we introduce assignments, the waters are considerably muddied.

```scala
val x = new BankAccount
val y = new BankAccount
```

Can we reasonably assume that `x` and `y` are the same? Well, no, obviously - but on what grounds do we base that answer? Is there something that tells you clearly that if you create two definitions with the same right-hand sides they give you back something different, whereas before it was always the same?

Where do you make that decision?

###Operational Equivalence
To respond to that question we must specift what is meatn by "the same". The precise meaning of being "the same" is defined by the property of *operational equivalence*

Suppose we have two definitions `x` and `y` - they are operationally equivalent if *no possible test* can distinguish between them.

To test if `x` and `y` are the same, we can essentially put them in a black box that consists of the definitions of `x` and `y`, and then an arbitrary sequence of instructions that pokes into these two definitions in any way possible, observing the possible outcomes.

```scala
val x = new BankAccount
val y = new BankAccount
f(x, y)
```

Then, we execute the definitions with another sequence `S'`, obtained by renaming all occurrences of `y` by `x` in `S`. 

```scala
val x = new BankAccount
val y = new BankAccount
f(x, x)
```

If the results are different, then the expressions `x` and `y` are certainly different. On the other hand, if all possible pairs of sequences `(S, S')` produce the same result, then `x` and `y` are the same.

Let's test these bank accounts:

```scala
val x = new BankAccount
val y = new BankAccount
x deposit 30
y withdraw 20 //java.lang.Error: insufficient funds
```

The initial balance of `y` is zero, and `x` and `y` are different - so we can't withdraw from an empty account.

Next, we rename every occurence of `y` in the sequence of operations to `x`

```scala
val x = new BankAccount
val y = new BankAccount
x deposit 30
x withdraw 20 
```

Now the second line works - the final results are different. So we can conclude that `x` and `y` define different objects, and are not the same.

On the other hand, if we define

```scala
val x = new BankAccount
val y = x
```

then no sequence of operations can distinguish between `x` and `y`, so they are the same.

To prove that two objects are the same is considerably harder - we have to show that no possible sequence of operations can distinguish between them. 

###Assignment and Substitution Model
The preceding examples show that our model of computation by substition has become problematic - in fact, it cannot be used. 

Indeed, if we apply the substitution model to our `BankAccount` example, we can always replace the name of a value by the expression that defines it. eg, 

```scala
val x = new BankAccount
val y = x
```

could rewrite to

```scala
val x = new BankAccount
val y = new BankAccount
```

But we have seen that this change leads to a different program! OH GOD

The substitution model as a whole stops being valid once we add assignment to a language. 

It is possible to adapt the substitution model by introducing a *store* that keeps track of all the references, but this becomes considerably more complicated.

So, with some regret, it's goodbye to the substitution model for all code that's not purely functional.