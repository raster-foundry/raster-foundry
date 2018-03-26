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
