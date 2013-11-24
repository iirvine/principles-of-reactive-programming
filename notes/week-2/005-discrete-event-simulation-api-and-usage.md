#How to make it work?

The class `Wire` and functions `inverter`, `andGate` and `orGate` represent a small dscription language of digital circuits - now we need to give an implementation of this class and its functions which allow us to simulate circuits.

These implementations are based on a simple API for discrete event simulation which we're going to study first.

###Dafuq is discrete event simulation
A discrete event simulator performs *actions*, specified by the user at a given *moment*

an *action* is a function that doesn't take any parameters and which returns `Unit`. Everything an action does it does by side-effect

The *time* is simulated - it has nothing to do with actual wall-clock time.

###Simulation Trait
Concretely, we're going to write simulations inside objects that inherit from a trait called `Simulation`

```scala
trait Simulation {
    def currentTime: Int = ???
    def afterDelay(delay: Int)(block: => Unit): Unit = ???
    def run(): Unit = ???
}
```

where `currentTime` returns the current simulated time in the form of an integer.

`afterDelay` lets the user install a block of statements to be performed as an action at a time that is `delay` time units after the current time.

finally, there's a `run` function which let the user start the simulation and execute all installed actions until no further actions remain.

###Class diagram
A typical simulation application would be composed of several classes like so:

![img](http://i.imgur.com/PWF2yhN.png)

We have at the top our trait `Simulation` - that trait gives us the necessary tools to do any kind of discrete event simulation. 

`Simulation` be inherited by something a little more special, that gives us the tools to do basic circuit simulation; it'll contain the class `Wire`, and the gates for AND, OR, and Inverter.

One level further down, we'll have a trait that gives us more complex circuits like halfAdder and adder.

Finally, we'll have the concrete objects that the user wants to simulate, typically an object that can obtain all the functionality by inheriting from the aboce traits.

Let's look at the interface of this Gates layer.

###The Wire
A `Wire` should support three basic operations:

`getSignal: Boolean` - returns the value of the signal transported by the wire at the current simulated time. 

`setSignal(sig: Boolean): Unit` - modifies the value of the signal transported by the wire

`addAction(a: Action): Unit` - attaches the specified procedure to the *actions* of the wire. When the signal of a wire changes, certain things should happen. The things that should happen can be "installed" with a call to `addAction`

Here's an implementation:

```scala
class Wire {
    private var sigVal = false
    private var actions: List[Action] = List()
    def getSignal: Boolean = sigVal
    def setSignal(s: Boolean): Unit =
        if (s != sigVal) {
            sigVal = s
            actions foreach(_())
        }
    def addAction(a: Action): Unit = {
        actions = a :: actions
        a()
    }
}
```

Pretty self-explanatory.

Note that to get the simulation off the ground, once we add an action we need to immediately perform it a first time, otherwise the simulation will rest in an inert state forever. 

###The state of a wire
State for a wire is modeled by two variables:
* `sigVal` represents the current value of the signal
* `actions` represents the actions currently attached to the wire

###The inverter
We implement the inverter by installing an action on its input wire.

That way, that action would be performed each time the input wire changes.

The action would produce the inverse of the input signal after a delay of `InverterDelay` units of simulated time.

```scala
def inverter(input: Wire, output: Wire): Unit = {
    def invertAction(): Unit = {
        val inputSig = input.getSignal
        afterDelay(InverterDelay) { output setSignal !inputSig }
    }
    input addAction invertAction
}
```

###The AND gate
the action of the and gate produces the conjunction of input signals on the output wire. This happens after a delay of `AndGateDelay` units of simulated time.

```scala
def andGate(in1: Wire, in2: Wire, output: Wire): Unit = {
    def andAction(): Unit = {
        val in1Sig = in1.getSignal
        val in2sig = in2.getSignal
        afterDelay(andGateDelay) { output setSignal (in1Sig & in2Sig) }
    }
    in1 addAction andAction
    in2 addAction andAction
}
```

###The OR gate
a lot like the AND gate

```scala
def orGate(in1: Wire, in2: Wire, output: Wire): Unit = {
    def orAction(): Unit = {
        val in1Sig = in1.getSignal
        val in2sig = in2.getSignal
        afterDelay(orGateDelay) { output setSignal (in1Sig | in2Sig) }
    }
    in1 addAction orAction
    in2 addAction orAction
}
```
