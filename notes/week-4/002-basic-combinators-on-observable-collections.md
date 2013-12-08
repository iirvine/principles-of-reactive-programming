#Combinators on Observables

Now that we know how to derive `Observable` collections, let's look at some combinators!

![img](http://i.imgur.com/sAule9F.png)

We see all our well known friends. 

Now that we're living in an async world, `flatMap` and friends will be a little bit different than what we're used to in `Iterable` collections. 

![img](http://i.imgur.com/2l4eI5s.png)

Here's `map` - nothing different compared to `Iterable`. We get our input stream of observable values, and we have our function that turns coins into diamonds, and out comes an observable collection of diamonds. The only difference between an `Iterable` collection is that instead of *pulling* the values from the input collection, the values get *pushed* at us, and then we apply the transformation and push the values in the output collection.

Let's look at `flatMap`!

![img](http://i.imgur.com/1vux8xu.png)

Quite different than `map` - here's the problem. `flatMap` has no control over *when* the values appear on the input stream, since we're asynchronous. The first value is pushed in the input stream, and `flatMap` transforms that into another stream and starts pushing it out - but as it's pushing out the values on the output stream, the next value comes in on the input stream! 

When the second value appears, it applies the function, and starts to produce them on the output. As it's doing that, the third value might appear - so what we see is that even though all the outputs of the red input value are in the same order, same with the green and blue, *they're all merged together* in the output. 

`flatMap` is defined as usual as `map(f).flatten()`, but in this case, `flatten` really means "non deterministic merge".

Here's a simple example:

![img](http://i.imgur.com/Zu6OP4D.png)

```scala
val xs: Observable[Int] = Observable(3,2,1)
val yss: Observable[Observable[Int]] = 
  xs.map(x => Observable.Interval(x seconds).map(_ => x).take(2))
val zs: Observable[Int] = yss.flatten()
```

Super simple. 

First, we create an Observable stream that quickly fires off 3,2, and 1. On that stream, `xs`, we map `Observable.Interval` for `x` seconds - meaning, we're generating for each input value a stream that will tick at `x` seconds. 

So the first stream will wait for three seconds, tick, another three seconds, tick....

![img](http://i.imgur.com/kl150oo.png)

We did a `take(2)`, so those treams only produce two values. 

So, when we merge, the first value is the first value from the third stream - we `map` that back to the original value so we can see where it came from. Then, after two seconds, two values come in rapid succession, then we have the first value for three, the second for two, and lastly the second value for three. *WHEW*

So, how is `flatten` defined? Well, it takes an `Observable[Observable]` and it flattens them - how does it do that? Well, it flattens them stream by stream, pairwise. We only have to look at merging two streams and then we can know how to merge a stream of streams, by applying the simple merge recursively. 

###Merge

![img](http://i.imgur.com/d7ntWmF.png)

One thing to notice is that we have to be careful what we do with the terminating conditions when we're merging streams... in this case, the first stream erros out. The second keeps on going - what we see is that the merged stream *stops* as soon as one of its input streams has an error.

Typically, whenever we have any kind of combinations of streams, when one of them has an exception we fail fast and propagate the exception to the output stream. Only if all the input streams have terminated will the output stream terminate successfully.

###Flatten nested streams

![img](http://i.imgur.com/K8jQG1n.png)

```scala
val xs: Observable[Int] = Observable(3,2,1)
val yss: Observable[Observable[Int]] = 
  xs.map(x => Observable.Interval(x seconds).map(_ => x).take(2))
val zs: Observable[Int] = yss.concat()
```

What if we don't want to merge the output streams - we want them in the same order as the original input stream? 

In this case, starting with red(3), yellow(2), blue(1), we want the values in the output stream to be in the same order. We want all the red ones, then the green, then finally the blue.

The code is exactly the same as before - we have a `Observable(3,2,1)`, which we `map` to `Observable.Interval` of `x` seconds. We then `map` that to the value itself, and we only take the first two elements. 

The interesting part happens with `concat` - it's going to buffer all the input values until the right moment to output them. In this case, it will *wait* to output the green and blue values until it's seen all the red values, and it'll wait to output the blue values until it's output all the green values.

In general, `concat` is a little bit dangerous - it can require arbitrary buffering. You don't know how long a given stream will take - maybe it's even infinite. But as we'll see, when the `Observable` streams are generated from a `Future`, that's a case where we might use `concat` to make sure the values are generated in the right order. 

###Concat
![img](http://i.imgur.com/b3JnVMT.png)

We can see from the marble diagram that `concat` only terminates successfully when *both* input streams terminate successfully, and that it holds onto that yellow value until the first stream has terminated. Only then does it output that yellow value.

###Earthquakes
Let's look at a more realistic example - earthquakes!

```scala
def usgs(): Observable[EarthQuake] { ... }

class EarthQuake {
    def magnitude: Double
    def location: GeoCoordinate
}

object Magnitude Extends Enumeration {
    def apply(magnitude: Double): Magnitude = { ... }
    type Magnitude = Value
    val Micro, Minor, Light, Moderate, Strong, Major, Great = value
}
```

Earthquakes are a good example of a push based stream - we don't *poll* the earth for earthquakes, they just happen, and we're notified about them in real time. 

The class `EarthQuake` has a bunch of information about the quake, including where it happend and the magnitude. Since the data for the magnitude comes in as a `Double`, which is not very useful, we'll make it a bit more readable by mapping the values into a more readable notation. We'll take the categorization from Wikipedia, and replace the `Double` with the enumeration that gives us a more readble form.

###Mapping and filtering asynchronous streams
```scala
val quakes = usgs()

val major = quakes
    .map(q => (q.Location, Magnitude(q.Magnitude)))
    .filter { case (loc, mag) => mag >= Major }

major.subscribe({ case (loc, mag) => {
    println($"Magnitude ${mag} quake at ${loc}")
}})
```

We'll start by projecting out the location and magnitude for each earthquake. We are projecting the magnitude, which was a `Double`, and we're going to replace it with our more readable enum. 

We're going to then filter out all the earthquakes that are at least major. We have to do a pattern-match since we get back a tuple of location and magnitude.

Finally, we subscribe to the major earthquakes, and do a pattern match to get the location and the magnitude and print out our real time major earthquake stream.

###Reverse Geocode
Let's try to improve our code even more!

```scala
def reverseGeocode(c: GeoCoordinate): Future[Country] = { ... }

val withCountry: Observable[Observable[(Earthquake, Country)]] = 
    usgs().map(quake => {
        val country: Future[Country] = reverseGeocode(q.Location)
        Observable(country.map(country => (quake, country)))
})

val merged: Observable[(EarthQuake, Country)] = withCountry.flatten()

val merged: Observable[(EarthQuake, Country)] = withCountry.concat()
```

One of the things not so nice about the previous example is that the locations are given in Geocoordinates, lat,lon and altitude. Unless you're a super geek that doesn't tell you much. (*HRMPH*)

Let's use a reverse geocoding service which takes a `GeoCoordinate` and returns a `Future[Country]` - we're using `Future` here because we give the service one geocoordinate and it gives us back one country, making it a very natural use of a `Future`.

But how can we mix `Future`s with `Observable`s? 

Well, let's start with this `withCountry` function, which will return an `Observable[Observable[(Earthquake, Country)]]`. For each earthquake in our orginal stream, we're going to take the geocoordinates and look up the country. Somehow we're going to create this sort of double-nested observable stream. 

We take our original `usgs` stream, which we `map` a function over. Given the earthquake, we can get its location and lookup its reverse geocode. That gives us a `Future[Country]`. Remember that `Future` is also a monad, so we can `map` over it, and then we pair that up with the original earthquake data. 

Now, just like with our previous example, we have two possible ways to merge this `Observable[Observable]` into a single observable stream. We can either `flatten` it, or `concat` it.

In this case, we probable want to use `concat` - the earthquakes happen in a certain order, but the geocoordinate lookup for one earthquake can take *longer* than for another. If we `flatten` them, then the earthquakes can come out of order! Let's look at a picture:

![img](http://i.imgur.com/bXfZZ0S.png)

We've got a first earthquake that happens, and let's suppose looking up the country for that one takes a looooong time - load balancer in the datacenter that sends us to a slow machine maybe. The second one goes super quick, so that terminates and we pair them up in the output. Second one is also pretty quick. Now only then does the first earthquake appear. 

What we see is that the order of the output doesn't match the input - the earthquakes came in red, green, blue, but after the geocoding they came out green, blue, red. Maybe this is acceptable, but in some cases it's not. In that case, we want to `concat` the results.

![img](http://i.imgur.com/j1aHnUF.png)

If we concatenate the results, we buffer the values of every previous earthquake until the earlier earthquakes have been geocoded. We have to wait until the red earthquake returns, and then we can output the green and blue ones.

Since we know that the async `reverseGeocode` function only has one value, it's okay that we're waiting for an infinite amount of time. That stream always terminates.

###groupBy
The last operator we're going to look at is `groupBy`. It's super interesting. 

`groupBy` takes an `Observable[T]` and, using a key selector to find a common key for each value in the stream, it will return an observable stream of pairs of the key, and a nested stream that contains all the values `T` from the original input stream for which the key selector maps. 

Let's look at a picture!

![img](http://i.imgur.com/n6T2QbK.png)

In the diagram, we're grouping by shape. What we'll get is two streams, one that's circles, and another that's triangles. These collections are paired with their keys (ie, circle and triangle).

Let's take it out for a spin. Let's use `groupBy` to group our earthquakes by country.

```scala
val merged: Observable[(Earthquake, Country)] = withCountry.flatten()

val byCountry: Observable[(Country, Observable[(Earthquake, Country)])] = merged.groupBy { case (q, c) => c }
```

![img](http://i.imgur.com/wXR2ZLY.png)