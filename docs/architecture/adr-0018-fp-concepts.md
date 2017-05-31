0018 - Functional Programming Concepts from [Cats](https://github.com/typelevel/cats)
=====================================================================================

Context
-------

[PR #1728](https://github.com/azavea/raster-foundry/pull/1728) (and others)
have been introducing usage of the `cats` library to reduce boilerplate and
clean up certain areas of code. #1728 in particular expands on the use of
the `Validated` type, and takes advantage of the fact that is both
*fundamentally combinable* (i.e. it is a `Semigroup`) and *weakly chainable*
(i.e. it is an `Applicative` Functor).

##### What's a `Semigroup` and why do we need it?

If some type `A` is *fundamentally combinable*, we mean that two `A`s can be
"added together" via the `combine` method:

```scala
import cats.implicits._

/* String has a `Semigroup` instance, which injects the `combine` method */
val foo: String = "hi"
val bar: String = "there"

foo.combine(bar) // "hithere"

/* List too */
val list0 = List(1,2,3)
val list1 = List(4,5,6)

list0.combine(list1)  // List(1,2,3,4,5,6)

/* Option too, but only if its contents are also a Semigroup */
val op0: Option[String] = Some("hi")
val op1: Option[String] = Some("there")

op0.combine(op1) // Some("hithere")
```

Most if not all of the primitive types and containers in Scala have a
`Semigroup` instance. So does `NonEmptyList`, the error container type we
use in the interpreter to accumulate errors:

```scala
...
case ProjectRaster(id, _) => Invalid(NonEmptyList.of(UnhandledCase(id)))
...
```

This is handy, because the `Validated` type is also a Semigroup:

```scala
/* |+| is sugar for `combine` */
Invalid(NonEmptyList.of(1)) |+| Invalid(NonEmptyList.of(2))  // Invalid(NonEmptyList(1, 2))

/* `Invalid` overrides `Valid`! */
Valid("hi") |+| Invalid(NonEmptyList.of(1)) // Invalid(NonEmptyList.of(1))
```

This last point is the crux of how our interpreter accumulates all possible
errors. We recurse through the AST, gather all `Invalid` values, and
gradually combine them as we return up from the leaf nodes. The entire
structure will be `Valid` iff each node itself was `Valid`.

##### What's an `Applicative` and why do we need it?

If some type `A` is *weakly chainable*, we mean that *contextual*
information from one operation to another is passed, but the actual inner
value need not be. Consider `Option`:

```scala
def foo: Option[Int]
def bar(n: Int): Option[String]

for {
  f <- foo
    b <- bar(f)
    } yield {
      b ++ "!"
      }

      // or: foo.flatMap(f => bar(f).map(b => b ++ "!"))
```
^ this is a fairly ordinary thing to do, and is necessary when later
operations depend on the results of earlier ones. If `foo` has returned
`None`, `bar` would never happen and the whole structure would result in
`None`.

What if `bar` doesn't depend on `foo`?

```scala
def foo: Option[Int]
def bar: Option[String]

for {
  f <- foo
  b <- bar
} yield {
    (f, b)
}

// or: foo.flatMap(f => bar.map(b => (f, b)))
```

Similarly, if either `foo` or `bar` were `None`, the whole thing would fail.
But it turns out that we're typing too much in this case. Since `bar`
doesn't care about `foo`s value, but it *does* care about whether `foo`
succeeded or not (the "contextual information" mentioned above), we can
write this more succinctly:

```scala
import cats.implicits._
import cats.syntax._

def foo: Option[Int]
def bar: Option[String]

val res: Option[(Int, String)] =
  (foo |@| bar).map({ case (f, b) => (f, b) })

// or: (foo |@| bar).map( (_,_) )
```
And the behaviour will be the same as the `for` block above. `|@|` says
"chain these", and the `map` portion expects a pure function that takes the
outputs of (in this case) `foo` and `bar`, but only if both succeeded.

For `Validated`, this accumulates the errors across each individual step,
and doesn't "fail fast" like `Either`:

```scala
val v0: ValidatedNel[Error, Int] = ...
val v1: ValidatedNel[Error, String] = ...
val v2: ValidatedNel[Error, Boolean] = ...

def work(i: Int, s: String, b: Boolean): Tile

/* Each v* is attempted, but `work` only occurs if each were `Valid` */
val res: ValidatedNel[Error, Tile] =
  (v0 |@| v1 |@| v2).map(work)
```

The concepts of `Semigroup` and `Applicative` are not unique to Scala, and
transfer as-is to other functional languages. They let us express powerful
ideas with little code, and there are many generic functions that operate on
them (and collections of them).
