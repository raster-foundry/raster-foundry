0015 - Factories for Test Objects
======

Context
------

Currently our testing situation is a real drag and we sometimes don't test things because
it's too much effort, (e.g., `update` methods, as documented in
[#1146](https://github.com/azavea/raster-foundry/issues/1146). Recently that problem surfaced
when the update method on scenes was broken and we didn't know until we tried to use our
API to update scenes' `IngestStatus` when they were added to projects.

Creating objects for tests is hard. For example, if you wanted to create a scene with one user
and organization and visibility level, and another scene with a different user and organization
and visibility level, you'd currently have to:

1. Create the first organization (post to the `/organizations/` endpoint)
2. Create the first user in that organization (post to the `/users/` endpoint)
3. Create the first scene (post the scene to the `/scenes/` endpoint)
4. Create the second organization (another post to the `/organization/` endpoint)
5. Create the second user (another post to the `/users/` endpoint)
6. Create the second scene (post your new scene to the `/scenes/` endpoint)

Now you're ready to start testing. Good thing there are only two combinations you want to test!

...but not really. You want to filter by different things probably, you might want different
scenes with the same user and org but different visibilities, you might want any number of things,
and you shouldn't have to go through the steps above every time you need two specific scenes to
test with (nevermind if you care about varing the images or thumbnails attached to your
scene as well; some things are too gruesome to discuss in polite company).

Options to Solve the Problem
------

We have, as far as I can tell, at least three options for solving this problem, and two good ones.

### 1. Auxilliary Constructors

An [auxilliary constructor](https://www.safaribooksonline.com/library/view/scala-cookbook/9781449340292/ch04s04.html)
is a method defined on a class that takes some alternative set of arguments for initializing the class.

We could use auxilliary constructors to provide all option versions of constructors for each
class in our datamodel. For example:

```scala
case class Datasource(
  id: UUID,
  createdAt: java.sql.Timestamp,
  createdBy: String,
  modifiedAt: java.sql.Timestamp,
  modifiedBy: String,
  organizationId: UUID,
  name: String,
  visibility: Visibility,
  colorCorrection: Map[String, Any],
  composites: Map[String, Any],
  extras: Map[String, Any]
)
object Datasource {
  def apply(
    id: Option[UUID] = None,
    createdAt: Option[java.sql.Timestamp] = None,
    createdBy: Option[String] = None,
    modifiedAt: Option[java.sql.Timestamp] = None,
    modifiedBy: Option[String] = None,
    organizationId: Option[UUID] = None,
    name: Option[String] = None,
    visibility: Option[Visibility] = None,
    colorCorrection: Option[Map[String, Any]] = None,
    composites: Option[Map[String, Any]] = None,
    extras: Option[Map[String, Any]] = None
  ) = {
    /** construct a Datasource from the options above, probably with a bunch of getOrElses */
    ???.asInstanceOf[Datasource]
  }
}
```

#### Pros

This would let us create a datasource as `val ds = Datasource()` or
`val ds = Datasource(name="datasource2")`.

This is a pretty easy solution. We add a constructors that give us default behaviors
if we specify anything at all, and we can use that for easy and quick creation of
test objects with specific fields varied.

#### Cons

It's possible we'll have a collision in json formats if this lives in the datamodel project,
which is where the auxilliary constructor would have to live. It might become legal by
accident to post `{}` to `/api/scenes/`, which we certainly don't want.

This less _removes_ boilerplate than moves it around a little bit. While we would only
have to write the auxilliary constructors once, we would have to write _a lot_ of them.
We wouldn't save anything for models that we only create once in testing.

This is also pretty clearly a basic implementation of property based testing --
we provide objects meeting some specific criteria and make assertions about results, ignoring
a bunch of miscellaneous information. That's nice because it's simple and easy to see and
we don't have to wonder about what's going on under the hood ever, but it's not nice because
we don't have access to existing tooling for property based testing, and the extent of our
tests will be limited by our patience and commitment rather than by whatever large number
we might pick for examples in a proper property based testing framework.

### 2. Object Factories

Object factories would work like auxilliary constructors, but we would locate them in a
separate section of the application with different scope. For example, a datasource
factory could live in `test/factories/Datasource.scala`, and could look like:

```scala
package com.rasterfoundry.factories

import java.util.UUID
import com.rasterfoundry.datamodel.{Datasource, Visibility}

object DatasourceFactory {
  def create(
    id: Option[UUID] = None,
    createdAt: Option[java.sql.Timestamp] = None,
    createdBy: Option[String] = None,
    modifiedAt: Option[java.sql.Timestamp] = None,
    modifiedBy: Option[String] = None,
    organizationId: Option[UUID] = None,
    name: Option[String] = None,
    visibility: Option[Visibility] = None,
    colorCorrection: Option[Map[String, Any]] = None,
    composites: Option[Map[String, Any]] = None,
    extras: Option[Map[String, Any]] = None
  ): Datasource = {
    /** construct a Datasource from the options above, probably with a bunch of getOrElses */
    ???.asInstanceOf[Datasource]
  }
}
```

#### Pros

Everything good about auxilliary constructors, plus we get to organize these into a single package
and don't have to worry about what happens with our implicit json formats since they're out of scope.

#### Cons

We still have to write a lot of them.

### 3. Property based testing

Property-based testing solves the object creation problem by doing it for us with values
that meet our classes' and functions' requirements.
[Here](https://www.scala-exercises.org/scalacheck/properties) is an example of how that
works in the ScalaCheck library. ScalaCheck provides a `forAll` method that takes a function
returning a `Bool`, then throws parameters at the functions until something breaks. For
example, we might write:

```scala
val scenePost = forAll { (s: Scene.Create) => {
  val resp = magicPostingFunction(s) 
  /** assert some things about resp that return bools */
  hasSomeProperties resp
}}
scenePost.check
```

We can be more specific, providing our own generators by subclassing `org.scalacheck.Gen`.
This would allow us to ensure, for example, that we have scenes from different orgs or
different users or different values for cloud cover.

#### Pros

We don't have to do anything to get scenes to post. It's nice not to have to do things. Our
tests would become more general and target application behavior well, since we wouldn't be
able to make guarantees about the particular values of parameters that we pass in at test
time.

We would also get to focus on writing an easy set of classes for interacting with our API
to avoid writing a lot of boilerplate, so we might write a scala API client by accident.

#### Cons

Property-based testing doesn't seem especially suited to testing database operations, for
two reasons:

1. Database operations involve a lot of state. The input/output objects from an operation
   have some guarantees, but testing a filter operation without knowing values of what we
   posted could be hard. We could maybe have special generators for testing filtering, but
   then we're back in the writing special objects for every specific test hell that we
   started in. [These slides](http://scalacheck.org/files/scaladays2014/#1) work on testing
   database operations with ScalaCheck, but it looks hard and might make maintainability
   worse.
2. Our test database may not be able to serve requests quickly enough. In our current pattern,
   we frequently fail tests because the test database doesn't respond quickly enough. Multiplying
   each request by 100 could hardly help with that. We might, however, consider that a different
   problem and ignore it when choosing how to make our testing lives easier. We can probably mitigate
   this by not choosing huge numbers of examples, but there are obvious tradeoffs there.
   
More complicated routes like `ScenesToProjects` might also be challenging.

### 4. Mocking

Packages like [ScalaMock](http://scalamock.org/quick-start/)
in principle let us create mock objects from our datamodel that we could
then pass around instead of actual scenes. That sounds nice but I found setup annoying.
We'd still have to manually create each mock object we wanted to use for tests,
and getting mocks with specific properties for comparison is a bit of a drag. We shouldn't
do this.

Decision
------

We should create factories to make creating objects easier (2). We should do it now-ish, since
it requires no additional libraries and would let us delete a lot of stock objects in, e.g.,
[`ThumbnailsHelper.scala`](https://github.com/azavea/raster-foundry/blob/develop/app-backend/api/src/test/scala/thumbnail/ThumbnailsHelper.scala#L18-L28)
or
[`ProjectSpec.scala`](https://github.com/azavea/raster-foundry/blob/develop/app-backend/api/src/test/scala/project/ProjectHelpers.scala#L34-L44)
that exist just so we'll have objects to work with.

We should try out (3) on tests of database behavior, but not tests of the API.
We should use one simple model (like `Bands`),
one complicated model with related objects (like `Tools`),
and one complicated model that has a join table (like `UserToOrg`)
to get a sense of just how complicated it might be to use property based testing
to test out our logic in a variety of places in the backend.

Consequences
------

We'll need to do two things:

- move generic (I know that's a functional programming word but names of things are hard)
  object creation to factories
- refactor our testing code to use the generic constructors where appropriate
- actually write tests for things that are currently too messy to test
