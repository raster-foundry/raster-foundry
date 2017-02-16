Raster Foundry Style Guide
============

Comments
--------

Single line comments should be wrapped in `/**...*/` if they're for Scaladoc (i.e.
they're above methods, classes, traits, objects, or packages). Otherwise, `//`
is fine.

```scala
/** good */
object foo {
  ???
}

// bad
object foo {
  ???
}

/** fine */
object foo {
  val bar = ??? // magic number from somewhere
}
```

Multi-line comments should start with a `/**`, have following lines asterisks trail
the _second_ asterisk from the first line, and end with a `*/`:

```scala
/** This is a good multi-line comment
  *
  * good stuff
  */
```

```scala
/** This is a bad multi-line comment
 *
 *  bad stuff
 */
```

This is _different_ from Scaladoc's style, but it's how we've done things, and it's
more consistent with autoindentation in IntelliJ's IDEA and the spacemacs scala
layer.

Functions
---------

#### Multiple parameter lists

Follow [Scaladoc's suggestion](http://docs.scala-lang.org/style/declarations.html#multiple-parameter-lists):
line up multiple parameter lists that span more than one line on the opening parenthesis of each list.

```scala
protected def forResource(resourceInfo: Any)
                         (f: (JsonNode) => Any) 
                         (implicit urlCreator: URLCreator, configurer: OAuthConfiguration): Any = {
  ???
}
```

Class Declarations
---------

When a class extends many traits, the subsequent `with foo` blocks should be
indented four lines, with the last one followed by the opening curly brace.

```scala
class GoodClass extends Foo
    with Bar
    with Baz
    with Qux {
    ...
}
```

"Many traits" means the class declaration would be over 100 characters, though if
it's getting close and readability would be improved you should go ahead and separate
the lines anyway.

Long-hanging indents before `with` statements can be replaced with
`sed "s/\\s\{n\}\(with.*\)/    \1/" filename > filename.styled`,
optionally `sed -i` if you're feeling lucky.

Endpoints and Routes
---------

#### Methods

Folowing route definition, methods should be the outermost block of code, e.g.:

```scala
get {
  stuff {
    ...more stuff...
  }
} ~
post {
  authenticate {
    ...stuff...
  }
} ~ ...
```

Not:

```scala
authenticate {
  post {
    ...more stuff...
  }
} ~
stuff {
  get {
    ...more stuff...
  }
} ~ ...
```

Methods as the outermost block puts allowed methods in a consistent place and also
prevents the problem discussed in [issue #562](https://github.com/azavea/raster-foundry/issues/562/).

Methods should return results of explicitly defined functions, e.g.:

```scala
def userToInt(u: User): Int = ???

post {
  entity(as[User]) {
    complete {
      userToInt
    }
  }
}
```

This style allows explicitly testing the logic of the return value by testing the `userToInt`
function without having to surround that test in boilerplate for the endpoint.

#### Routes

Endpoints for returning several objecst are `list` endpoints, and should be named `list{object}` as
opposed to `get{object}s`, since `get{objects}s` is hard to distinguish visually from `get{object}`.
([Issue #475](https://github.com/azavea/raster-foundry/issues/475))

Static components of a package's routes should not be defined in the package itself, but in a
`Router` trait that has access to that information. Prefer:

```scala
// In the router trait
val routes = cors() {
  pathPrefix("healthcheck") {
    healthCheckRoutes
  } ~
  pathPrefix("api") {
    pathPrefix("foo") {
      fooRoutes
    } ~
    pathPrefix("bar") {
      barRoutes
    }
  } ~
  ???
}
```

to:

```scala
val routes = cors() {
  healthCheckRoutes ~
  fooRoutes ~
  barRoutes ~
  ???
}
```

Slick
---------

#### Wide Tables

Any table with > 22 fields cannot use the handy

```scala
def * = (...) <> (T.tupled, T.apply)
```

default projection that we make use of in so many places because the number of fields
is too many for `TupledXX`. For tables like these, the best solution so far is using
nested case classes to group related fields. See the `Scenes` table for an example
or this [StackOverflow question and answer](http://stackoverflow.com/questions/28305023/custom-mapping-to-nested-case-class-structure-in-slick-more-than-22-columns).

#### Queries with several actions

Slick gives us several options for combining DBIO actions. Use for-comprehensions anywhere
the two actions depend on each other (e.g., the value `a` below is necessary for `b`).

```scala
for {
  a <- db.someAction()
  b <- db.someActionUsing(a.id)
} yield (a, b)
```

In addition to making `a` available for the second action, for-comprehensions fail fast. If
`a` were to fail, the operation to attain `b` wouldn't even be attempted. We'd get back an
empty (failed) future.

For general, possibly unrelated sequences of actions, also use for-comprehensions and ensure
to mark them transactional

```scala
for {
  a <- db.someAction()
  b <- db.someAction()
  c <- db.someAction()
} yield (a, b, c) .transactionally
```

We _could_ use `DBIO.seq` and mark the sequence transactional, but using for-comprehensions
will:

- ensure style consistency for all sequences of actions, related or not
- ensure atomic success or failure with the presence of `.transactionally`
- yield futures that we can then use instead of yielding `Unit`

#### Complicated joins

If you're chaining several join operations, use pattern matching to make code explicit:

```scala
// good
for {
  (((scene, image), band), thumbnail) <-
  (scenes
    joinLeft Images on { case (s, i) => s.id === i.scene }
    joinLeft Bands on { case ((s, i), b) => i.id === b.imageId})
    joinLeft Thumbnails on { case (((s, i), b), t) => s.id === t.scene})
}
```

This is much more readable than relying on future readers to grasp quickly what each
underscore refers to.

Migrations
---------

#### Array columns should not be nullable, default to empty list

Both in Scala and SQL, array columns should be `NOT NULL` and be initialized to
an empty list:

```scala
val metadataFiles: Rep[List[String]] =
  column[List[String]](
    "metadata_files",
    O.Length(2147483647,varying=false),
    O.Default(List.empty)
  )
```

```sql
ALTER TABLE scenes
  ADD COLUMN metadata_files text[] DEFAULT '{}' NOT NULL;
```

General Rules
---------

- Don't have hanging indents over 50 characters.
- Try to keep things on one line unless readability would be improved otherwise.
