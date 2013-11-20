##Dafuq is Reactive Programming

Changing requirements...

![img](http://i.imgur.com/CMev38n.png)

Need new architectures.

*Previously*: Java Enterprise architecture; managed servers and application containers

*Now*: Reactive applications; event driven, scalable, resilient, and responsive.

###Event Driven

Traditionally, concurrent systems were composed of multiple software threads, communicating with shared, synchronized state.

That lead to a high degree of coupling, and such systems were hard to compose.

*Now*: systems are composed from loosely coupled event handlers; events can be handled asynchronously, without blocking. Because there is no blocking, typically resources can be used more efficiently.

###Scalable

An application is *scalable* if it is able to be expanded according to its usage. Typically we distinguish two directions of scaling:

* scale up: make use of parallelism in multi-core systems
* scale out: make use of multiple server nodes

Important for scalability: minimizing shared mutable state. 
Important for scale out: location transparency (it shouldn't matter *where* a location is located; could be at the same computer as a client or at some other computer across the internet. The functionality should stay the same) and resilience

###Resilient

An application is *resilient* if it can recover quickly from failures.

Typically, resilience cannot be added as an afterthought - it needs to be part of the design from the beginning.

Needed:

* loose coupling
* strong encapsulation of mutable state
* pervasive supervisor hierarchies

###Responsive

An application is *responsive* if it provides rich, real-time interaction with its users even under load and in the presence of failures.

###Callbacks

Event handling is nothing new - it's often done using callbacks.

```scala
class Counter extends ActionListener
    private var count = 0
    button.addActionListener(this)

    def actionPerformed(e: ActionEvent): Unit = {
        count +=1
    }
```

We know the score here - we register ourself to be called back when there's an event, and every time that event gets triggered we perform an action, like incrementing that stupid counter.

This has quite a few problems:

* needs shared mutable state; the return type of our `actionPerformed` method us `Unit`, so to have any effect at all the method needs to have a side-effect, in this case, on the variable `count`. A design using listeners and callbacks naturally leads to shared mutable state
* the second problem is that it's very hard to construct higher abstractions out of simple listeners; event handlers have a hard time being composed
* leads quickly to "callback hell"; a big web of callbacks that are hard to track and understand

###How to do Better

Use fundamental constructions from functional programming to get *composable* event abstractions. 

* Events are first class; 
* events are often represented as messages
* handlers of events are also first class
* complex handlers can be composed from primitive ones
