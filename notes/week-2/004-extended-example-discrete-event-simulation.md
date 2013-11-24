#Discrete Event Simulation

The central idea: we'll use mutable variables to simulate changing quantities in the real world. 

Concretely, we'll construct a digital circuit simulator. It's based on a general framework for discrete event simulation.

###Digital Circuits
Let's start with a small description language for digital circuits.

A digital circuit is composed of *wires* and of functional components.

Wires transport signals that are transformed by the components.

Becaues we are working with digital signals and not analog ones, we just represent signals using booleans `true` and `false`.

The base components (gates) are:
* the *inverter*, whose output is the inverse of its input
* the *AND gate* whose output is the conjunction of its inputs
* the *OR gate* whose output is the disjunction of its inputs

Other components can be constructed by combining these base components.

The components have a reaction time (or *delay*) that means their outputs don't change immediately after a change to their inputs.

###Digital Circuit Diagrams
Digital circuits are usually described by diagrams; the basic components of these diagrams, the gates, are drawn like this.

Here's the Inverter:
![img](http://i.imgur.com/hicKggu.png)
It transforms its input on the left to its output on the right.

Here's the AND gate:
![img](http://i.imgur.com/63kJTiE.png)
It takes two inputs and takes the conjunction in the output; the output is true if both inputs are true.

Finally here's the OR gate:
![img](http://i.imgur.com/imhOhWJ.png)
It takes two inputs and converts them to an output which is true if either of the two inputs is true.

We can use these basic gates to construct more interesting circuits!

Here's how we would construct a half-adder, that takes two inputs and converts them into a sum and a carry. We'll have two inputs, we'll call them `a` and `b`.

We form first the AND of `a` and `b`, as well as the OR - then we place an inverter after the AND, and finally another AND gate. That gives us our sum and carry on the other side of the circuit.
![img](http://i.imgur.com/gWQCUQL.png)


So, the sum is set if either `a` or `b` are set and NOT both of them are set; if both `a` and `b` are set, the carry is set.

###A language for Digital Circuits
We'd like a textual language to describe these kinds of circuits. To start with, the class `Wire` models wires. (duh)

Wires can be constructed as follows:

```scala
val a = new Wire; val b = new Wire; val c = new Wire
```

or, equivalently: 

```scala
val a,b,c = new Wire
```

One thing that's important is that a wire is not just a straight line, but everything that's connected until it hits a gate. 

###Gates
Then, we have the following functions. 

![img](http://i.imgur.com/Q8gsXQs.png)

They take `Wire`s as inputs, and places the gate as a side-effect on the circuit board. 

###Constructing Components
How would we build our half-adder with this language?

```scala
def halfAdder(a: Wire, b: Wire, s: Wire, c: Wire): Unit = {
    val d = new Wire
    val e = new Wire
    orGate(a,b,d)
    andGate(a,b,c)
    inverter(c,e)
    andGate(d,e,s)
}
```

Our half-adder takes four wires, our inputs `A` and `B`, and the two outputs, `S` and `C. 

What we need to do then is create two internal wires, `d` and `e` - then we place our gates as before, and that wrapped up gives us a component that we call `halfAdder`. It is its own function, so we can use it as another component just in the same way we can use OR Gates, AND Gates, and Inverters. We can use it to place between input and output wires.

###Moar Components!
our half-adder can be used to define a full adder:
```scala
def fullAdder(a: Wire, b: Wire, cin: Wire, sum: Wire, cout: Wire) Unit = {
    val s = new Wire
    val c1 = new Wire
    val c2 = new Wire
    halfAdder(b, cin, s, c1)
    halfAdder(a, s, sum, c2)
    orGate(c1, c2, cout)
}
```

A full adder takes three inputs, `a` and `b`, and the input carry `cin`. It works by placing a half adder between the `b` and the input carry, then placing another half adder between the `a` and the sum of that first half adder. That sum result will give us the final result.

We produce the output carry `cout` by taking the OR of the two carries of the half adder. 

Here's a drawring!
![img](http://i.imgur.com/r7YFuAm.png)

Now we have a full one-bit adder, which in turn we could use to produce an eight bit adder, 16 bit adder, or any other circuit we want.