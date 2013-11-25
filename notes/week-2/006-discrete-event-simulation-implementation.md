#The Simulation Trait
All that's left is implementing the `Simulation` trait - the idea is to keep in every instance of `Simulation` an *agenda* of actions to perform.

The agenda is a list of (simulated) *events*. Each event consists of an action and the time when it must be produced.

The agenda list is sorted in such a way that the actions to be performed first are in the beginning. That we, we can simply pick them off the front of the list to execute them.

```scala
trait Simulation {
    type Action = () => Unit
    case class Event(time: Int, action: Action)
    private type Agenda = List[Event]
    private var agenda: Agenda = List()
}
```

###Handling time
There is a private variable `curtime`, that contains the current simulation time. It can be accessed with a getter function `currentTime`.

An application of the `afterDelay(delay)(block)` method inserts the task `Event(curtime + delay, () => block)` into the agenda list at the right position.

```scala
def afterDelay(delay: Int)(block => Unit): Unit = {
    val item = Event(currentTime + delay, () => block)
    agenda = insert(agenda, item)
}

private def insert(ag: List[Event], item: Event) List[Event] = ag match {
    case first :: rest if first.time <= item.time =>
        first :: insert(rest, item)
    case _ =>
        item :: ag
}
```

###The event handling loop

The event loop removes successive elements from the agenda and performs the associated actions

```scala
private def loop(): Unit = agenda match {
    case first :: rest =>
        agenda = rest
        curtime = first.time
        first.action()
        loop()
    case Nil =>
}
```

###The run method
The `run` method executes the event loop after installing an initial message that signals the start of the simulation 

```scala
def run(): Unit = {
    afterDelay(0) {
        println("*** Simulation started, time = " +currentTime+" ***")
    }
    loop()
}
```

###Probes
Before we can launch the simulation, we need a way to examine the changes of the signals on the wires. To this end, we define the function probe. 

```scala
def probe(name: String, wire: Wire): Unit = {
    def probeAction(): Unit = {
        println(s"$name $currentTime value = ${wire.getSignal}")
    }
    wire addAction probeAction
}
```

`probe` is something we can add to a wire, just like a gate or another component. The action consists of printing the name of the wire, the current time, and the new signal on that wire. 

###Defining technology-dependent parameters
It's convenient to pack al delay constants into their own trait which can be mixed into a simulation.

```scala
trait Parameters {
    def InverterDelay = 2
    def AndGateDelay = 3
    def OrGateDelay = 5
}
```

then we can just go

```scala
object sim extends Circuits with Parameters
```