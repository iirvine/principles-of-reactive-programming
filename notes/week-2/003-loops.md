#Loops

*Proposition* - Variables are enough to model all imperative programs. But what about control statements like loops?

We can use functions for that!

here's a program that uses a `while` loop:

```scala
def power(x: Double, exp: Int): Double = {
    var r = 1.0
    var i = exp
    while (i > 0) { r = r * x; i = i - 1 }
    r
}
```

How could we define `while` using a function? 

```scala
def WHILE(condition => Boolean)(command: => Unit): Unit =
    if (condition) {
        command
        WHILE(condition)(command)
    }
    else ()
```

`WHILE` takes a `condition` and a `command`. It's result type would be `Unit`. It will do a recursive call to `WHILE` if the condition holds, with the same condition and command.

One thing to note - both the condition and the command must be passed by name so that they're reevaluated in each iteration. Otherwise, if the condition was by value and maybe true, the while loop would loop forever because the condition would never be reevaluated to be false.

Note that `WHILE` is tail recursive, so it can operate with a constant stack size - it is just as efficient as a native while.

We can also write a function implementing a `repeat` loop:

```scala
def REPEAT(command: => Unit)(condition: => Boolean) =
    command
    if (condition) ()
    else REPEAT(command)(condition)
```

###For-loops
The classical for loop in java cannot be modeled simply by a higher-order function.

The reason is that a for-loop like 

```java
for (int i = 1; i < 3; i = i + 1) { 
    System.out.print(i + " ") 
}
```

the arguments of `for` contain the *declaration* of the variable `i`, which is used later on in other parts of the for-loop. 

This isn't something that can be achieved in a straight-forward way by using just higher-order function applications. 

In scala, we have something similar to java's extended for loop:

```scala
for (i <- 1 until 3){
    System.out.print(i + " ")
}
```

###Translation of For-Loops
This looks a lot like a for-expression - that's no accident. The translate quite similarly.

But where for-expressions translate into combinations of `map` and `flatMap`, for-loops translate into combinations of the function `foreach`.

`foreach` is a a function defined on all collections with elements of type `T`

```scala
def foreach(f: T => Unit): Unit =
    //apply `f` to each element of the collection
```

It takes a function of type `T` to `Unit` and gives you back a `Unit` - its effect would be to apply the given function argument `f` to each element of the collection.

![img](http://i.imgur.com/0ins3yN.png)
