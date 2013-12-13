#Actors
Roland's here to give us the low down on actors.

Dafuq are actors?

First, let's talk about some motivating problems.

###Threads
In the past, programs just got faster by using the next generation of CPUs - but CPUs aren't getting faster anymore, they're getting *wider*.

* multiple execution cores within one chip, sharing memory
* virtual cores sharing a single physical execution core

Programs running on the computer must feed these cores

* running multiple programs in parallel (multi-tasking)
* running parts of the same program in parallel (multi-threading)

To achieve multi-threading, our program must be written in a different way than the traditional sequential form. 

The difference between running seperate programs in parallel and using threads of the *same* program in parallel is that these threads collaborate on a common task - just like a group of people doing something together, the threads need to synchronize their actions, or else there will be much toe-stepping. 

###Example

```scala
class BankAccount {
    private var balance = 0

    def deposit(amount: Int): Unit =
        if (amount > 0) balance = balance + amount

    def withdraw(amount: Int): Int = 
        if (0 < amount && amount <= balance) {
            balance = balance - amount
            balance
        } else throw new Error("insufficient funds")
}
```

Let's take a look at the good 'ole bank account, introduced by Martin a few classes back. 

It has a field `balance`, and two methods to deposit an amount and withdraw an amount. Let's look at the `withdraw` method in detail, and mark out the places where the `balance` is read and where it is written to:

```scala
def withdraw(amount: Int): Int = 
    val b = balance
    if (0 < amount && amount <= b) {
        val newBalance = b - amount
        balance = newBalance
        newBalance
    } else throw new Error("insufficient funds")
```

What happens if two threads run this code at the same time?

Let's say we've got Thread1, and Thread2. They're executed by different CPUs so they can run nearly in parallel. 

They enter the method with an amount (say Thread1 wants to withdraw 50, and Thread2 wants to withdraw 40).

The first thing they'll both do is read the current `balance` - let's say right now it's 80. They will both see 80, and then both will enter the if statement - is the amount relative, and are there actually sufficient funds in the account. Both conditions hold true, so both threads will continue.

They'll calculate the new balance - in the first thread, it'll be 30, and in the second, it'll be 40.

Finally, they'll write the back the new balance into the `balance` field of the `BankAccount` object. Thread1 will write 30, and Thread2 will write 40. 

Clearly, this is in conflict - only one of the writes can win in the end. The one which comes last will overwrite the one that comes earlier.

This is the first problem - one of the updates to the balance is actually lost. The other problem is that the invariant of the `BankAccount` is violated - we have withdrawn 50 && 40 swiss francs from our `BankAccount` when we only had a balance of 80. That should not have been possible! One of the threads should have failed. That this failure didn't occur is the other problem with this code.

###Synchronization

We've got multiple threads stepping on each other's toes. When multiple threads are working with the same data, they need to synchronize their actions. We need to make sure that when one thread is working with the data, the others keep out. 

We have to demarcate regions of code with "don't disturb" semantics, and make sure that all access to shared state is protected.

![img](http://i.imgur.com/WAYBVr2.png)

This way the `balance` will be protected, and all modifications done on it are in a consistent fashion, one after the other - *serialized*.

The primary tools for accomplishing this are lock, mutex, or semaphore. 

In scala, every object has a lock: `obj.synchronized { ... }`. The `synchronized` method accepts a code block which will be executed in this protected region.

How would we apply this to our `BankAccount`?

```scala
class BankAccount {
    private var balance = 0

    def deposit(amount: Int): Unit = this.synchronized {
        if (amount > 0) balance = balance + amount        
    }

    def withdraw(amount: Int): Int = this.synchronized {
        if (0 < amount && amount <= balance) {
            balance = balance - amount
            balance
        } else throw new Error("insufficient funds")        
    }
}
```

If we put our entire `withdraw` method in a `synchronized` block, all of it - reading the balance, performing the check, writing it back - will be done as one atomic action, which cannot be disturbed by another thread also trying to `withdraw` at the same time. 

Why do we also have `synchronized` on the `deposit` method? Well, it also modifies the `balance` field - if it wasn't synchronized, it could modify it without protection, and once the `withdraw` writes the balance back, it would overwrite the update performed by depositing at the same time. *ALL* accesses to `balance` need to be synchronized. 

Let's try to transfer some money from one `BankAccount` to another:

```scala
def transfer(from: BankAccount, to: BankAccount, amount: Int): Unit = {
    from.synchronized {
        to.synchronized {
            from.withdraw(amount)
            to.deposit(amount)
        }
    }
}
```

We need to synchronize both objects such that they're in a consistent state - otherwise, someone reading the balance of the accounts could find the money in-flight. During the time we're sending the amount from one account to another, the money is basically *nowhere*. If the invariant that needs to be enforced is that the sum of `from` and `to` needs to be the same, this would be violated. 

Once we've taken a lock on `from` and `to`, we're safe that no other thread can modify these accounts, but we can. One property of locks in scala is that they are re-entrant, meaning the same thread can take the lock twice or any number of times. It's just a protection against other threads.

###Composition of Synchronized Objects
There's a problem with this code - it introduces the possibility of a *dead-lock*.

Let's say that one thread wants to `transfer(accountA, accountB, x)`, and another thread tries to transfer in the opposite direction - `transfer(accountB, accountA, y)`. 

If both start at the same time, they take the first lock - Thread1 on `accountA`, thread 2 on `accountB`. Then they go and try to take the other lock, and they won't succeed - there's already a lock on the other thread. 

Now, none of the threads can make progress, and they'll both be stuck forever. There's no chance either of them will yield the lock they already have. 

There are solutions for this, for example, always taking the locks in the same order. You'd have to define an ordering for `BankAccount` and so on, and then we could potentially solve this. 

You'll find that these solutions aggregate and make your code complicated over time - what if you want objects from different code bases that you cannot modify to collaborate?

###We want non-blocking objects
It would be much better if our objects did not require blocking synchronization - blocking is what really makes the dead-lock happen. 

Blocking is also bad for CPU utilization. If there are other threads to run then the OS will run them, otherwise the CPU will be idle. Waking it up, or getting a thread back to running when some other thread has interrupted it takes a lot of time. 

Another problem with blocking objects is that synchronous communication couples sender and receiver quite strongly - the sender needs to wait until the receiver is ready. If we call a `BankAccount` that's synchronized, it's going to block me until it's ready. 

Non-blocking objects are exactly what actors are! We'll see that next.