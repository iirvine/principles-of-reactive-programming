#Subscriptions
Let's look a little bit deeper at the mechanisms underneath `Observable`.

Say we wanna unsubscribe from a stream...

```scala
val quakes: Observable[Earthquake] = ...
val s: Subscription = quakes.Subscribe(...)

s.unsubscribe()
```

If we're not interested in receiving anymore updates, we `unsubscribe` from the subscription. We retract our observer from the stream, and we'll no longer be notified of any future updates.

###Hot and Cold

It's important to distinguish between two kinds of observables - the first are called *hot* observables. 

![img](http://i.imgur.com/a67oNwh.png)

A "hot observable" is an `Observable` where all subscribers share the same source. This source will produce vaulues independent of how many subscribers there are, and independent of whether the subscribers come and go. 

UI events are an example of these so-called hot observables - your mouse moves happen independently of how many event handlers you have subscribed to them.

The other kind are called *cold* observables.

![img](http://i.imgur.com/1BjHQxc.png)

Cold observables has it's own private source for each subscriber - for example, the observable stream of earthquakes is a cold observable. Nothing happens *until you subscribe to it* - in which case, it will make a network call to the service and start producing all the values.

###Unsubscribing != Cancellation
The difference between hot/cold observables becomes important when we start to talk about unsubscription vs cancellation. 

In general, unsubscription just means that the observer that unsubscribes doesn't get any further notifications. There may be other active observers, so when we unsubscribe one it doesn't mean we can cancel the computation happening in the `Observable[T]`. 

Erik's gonna be sloppy and use cancel/unsubscribe interchangeably; but generally, "unsubscribe" doesn't mean that the underlying computation gets canceled. Most APIs will try to do a best-effort to cancel the computation in the case that no subscribers are left.

###Subscriptions

```scala
trait Subscription {
    def unsubscribe(): Unit
}

object Subscription {
    def apply(unsubscribe: => Unit): Subscription
}
```

A `Subscription` has only a single method, `unsubscribe`, that returns `Unit`. The companion object has an `apply` method that takes a block of work `unsubscribe` and returns `Subscription`.

There's many many kinds of subscriptions -let's look at a few.

![img](http://i.imgur.com/xWmL7Zg.png)

A `BooleanSubscription` has an additional method, `isUnsubscribed`, that we can use to check if this particular subscription has been unsubscribed or not. 

A `CompositeSubscription` is like a collection of subscriptions - we can add and remove subscriptions, and when we unsubscribe on the `CompositeSubscription` all inner subscriptions get unsubscribed.

A `MultipleAssignmentSubscription` is like a proxy for an inner subscription that we can set and replace, but there's always one inner subscription active.

Here's some sample code using the simples form of `Subscription`!

```scala
val subscription = Subscription {
    println("bye bye, I'm out fishing")
}

subscription.unsubscribe()
subscription.unsubscribe()
```

We need to make sure that the work we do when we call `unsubscribe` is idempotent - `unsubscribe` can be called multiple times. Typically what happens is that the implementation of `Subscription` will call `unsubscribe` and print "bye bye, I'm out fishing" once - the second time, the subscription will remember that it's already been unsubscribed, and it will not print a second time. 

But if we implement our own subscriptions, we need make sure they're idempotent, that we can call `unsubscribe` many times. It could be that hand out a `Subscription` to multiple threads, and each of these threads independently of each other might want to call `unsubscribe`.

###BooleanSubscription
```scala
val subscription = BooleanSubscription {
    println("bye bye, I'm out fishing")   
}

println(subscription.isUnsubscribed)
subscription.unsubscribe()
println(subscription.isUnsubscribed)
```

`BooleanSubscription` will tell us if a `Subscription` has been unsubscribed. 

![img](http://i.imgur.com/EJGCrS4.png)

When we draw pictures of subscriptions, we'll use a solid circle to indicate a subscription that has not been unsubscribed, and a dashed circle to indicate one that has been unsubscribed. 

###CompositeSubscription
```scala
val a = BooleanSubscription { println("A") }
val b = Subscription { println("B") }

val composite = CompositeSubscription(a, b)

println(composite.isUnsubscribed)

composite.unsubscribe()

println(composite.isUnsubscribed)
println(a.isUnsubscribed)

composite += Subscription{ println("C") }
```

When we add some subscriptions to a `CompositeSubscription`, what we get is one `CompositeSubscription` containing two sub-subscriptions - all three of which are solid because they've not been unsubscribed. When we ask if the composite is unsubscribed, it will return false. 

If we now unsubscribe from the `CompositeSubscription`, `isUnsubscribed` will return true. If we ask the `BooleanSubscription` `a` if it's unsubscribed, it will also return true. Once we unsubscribe from the `CompositeSubscription` all inner subscriptions will also be unsubscribed. It behaves like a set of subscriptions that all get unsubscribed together.

![img](http://i.imgur.com/Wc4X0dH.png)

What happens if we have another `Subscription` `c` that we add to the `CompositeSubscription` *after* we've already unsubscribed it? 

![img](http://i.imgur.com/yM5qXmv.png)

Well, there's two possibilities - if our `CompositeSubscription` has not yet been unsubscribed, adding `c` to it will do nothing, it will just add `c` and not touch it. 

However, if we try to add `c` to an already unsubscribed composite, we will *eagerly unsubscribe* from `c`.

###MultiAssignment

```scala
val a = Subscription { println("A") }
val b = Subscription { println("B") }

val multi = MuliAssignmentSubscription()

println(multi.isUnsubscribed)

multi.subscription = a
multi.subscription = b

multi.unsubscribe()

multi.subscription = Subscription{ println("C") }
```

When we change the inner subscription of a `MultiAssignment` subscription, say from `a` to `b`, `a` just gets removed and `multi` now just contains `b`. As soon as we unsubscribe from the `MultiAssignment`, `b` will also be unsubscribed. 

The difference between a MultiAssignment and composite subscription is that a MultiAssignment will always have a single subscription that gets replaced, whereas a composite has a whole set of subscriptions.

But wait, let's ask the same question as before: what happens when we change the subscription of a MultiAssignment once it's already been unsubscribed?

![img](http://i.imgur.com/VDoPfl1.png)

The answer is unsurprising - same thing as before. It will eagerly call unsubscribe on the new subscription.

One last question: what happens if we have a MultiAssignment or composite subscription where the inner subscription gets unsubscribed from the outside?

Well, since the outer subscription has no way to get notified of any changes in the inner subscription, the outer subscription still needs to be unsubscribed. 