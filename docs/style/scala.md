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

Doobie
------

### Where should my Dao live?

If you're making a new Dao, it goes in its own file, and the file should have the name
of the Dao you're creating. No more `SceneDao.scala` that also has `SceneWithRelatedDao`
inside. It makes debugging hard as you try desperately to keep track of which object
you're in, fail anyway, then get confused about why the behavior of the code isn't
changing.

### How should I test my Dao?

_Every_ method you include on your Dao should have a test if it includes a fragment.
To test your Dao, make sure that there is an `Arbitrary` instance available for the case
class your Dao's rows resolve to, then write a property test for what should happen if
someone calls your Dao's methods. We want to do this to ensure that we've written valid
sql everywhere, since the fragments themselves are just strings that have no checking
done on them unless we explicitly ask. See the `UserDaoSpec` for examples of property
tests, and see `Generators.scala` in the datamodel subproject for `Arbitrary` examples.

### What's an unsafe method?

Unsafe methods are methods that make strong assumptions about the state of the world and
fail to handle exceptional conditions. For example, if a user is looking up an item by id,
it's normally going to be the case that they didn't make the id up out of thin air, but
sometimes, the id won't be present in the database anyway. For this reason, a safe
`getFooById` should always return a `ConnectionIO[Option[Foo]]`, while if you want to
return a `ConnectionIO[Foo]`, you should name your method `unsafeGetFooById` to make it
clear to users that it can fail in an unhandled way.

### What should I do with purely side-effecting IO?

This question is about the following scenario:

- you want to do something in the database (add a user to a group, for example)
- afterward, you want to do some kind of side-effect (logging, sending an email...) that can
  fail
- you don't care about whether the side-effect fails for whether the initial action should fail

In that case, you should, supposing you have `addUserToGroupIO <* sendEmailIO`, you should
`.attempt` the second IO -- `attempt(effect: M[A]): M[Either[Throwable, A]]`. Using `.attempt`
will protect the first effect from failures in the second effect. `.attempt` is available on
`ConnectionIO`s in `doobie` and on `IO`s in `cats.effect`.

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
