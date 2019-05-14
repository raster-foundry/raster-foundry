# 0025 - User Access Scopes

## Context

As more applications have come to interact with the Raster Foundry API, the need to control user permissions on a different axis has become more apparent.
Currently, the Raster Foundry API handles permissions that refer to specific object instances. Each access control rule assigned to a user points to a specific object by id and indicates the allowed action.
This system does not support access control across object types, such as a rule that encodes "this user cannot create projects" or "this user cannot delete annotations".
This ADR seeks to develop a solution to this problem.

## Technical goals

- minimize the performance overhead related to evaluating user-scopes per-request
- minimally, develop an approach that prevents certain users from creating new objects (projects, scenes, etc.)
- ideally, develop an approach that is flexible enough to handle unforeseen access control scenarios

## Solutions

The solutions below (except for the last) all treat scopes as access reduction mechanisms. That is, full access is defined by the absence of scopes while the presence of scopes reduces the access of the user in question. There are a few reasons for this:

- It is assumed that a majority of users will not have any access restrictions
- In the most common application of this system (preventing project creation), specifying what _can_ be done would require _many_ more rules than specifying what _can't_ be done

### The Basic Solution

- Each user has a column representing a list of read-only scopes for that user (i.e. for some user the column named `scopes` has a list containing `PROJECT` and `SCENE`)
- Every value in the scope list is a member of `ObjectType`
- Each API endpoint that is affected would do a lookup on the requesting user and see if there was a scope present that should prevent the request from being fulfilled.

Usage at the endpoint level might look like:

```scala
  def createProject: Route = authenticate { user =>
    authorizeAsync {
      UserDao
        .withoutScoping(ObjectType.Project)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Project.Create]) { newProject =>
        onSuccess(
          ProjectDao
            .insertProject(newProject, user)
            .transact(xa)
            .unsafeToFuture) { project =>
          complete(StatusCodes.Created, project)
        }
      }
    }
  }
```

#### Advantages

- no possible conflicts as this only handles read-only restrictions
- minimal effect on current data-models -- requires one additional field on `User`

#### Disadvantages

- only supports read-only scoping
- requires some machinery to encode/decode scopes to/from the DB

### The Wild West Solution

This solution has almost no constraints.

- Each user has a column representing a list of scopes, for example `ProjectReadOnly` or `ProjectNoDelete`
- These scopes could be defined within a `ScopeType` to enforce values
- Each API endpoint that is affected would do a lookup on the requesting user and see if there was a scope present that should prevent the request from being fulfilled.

Usage at the endpoint level might look like:

```scala
  def createProject: Route = authenticate { user =>
    authorizeAsync {
      UserDao
        .withoutScoping(ScopeType.ProjectReadOnly)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Project.Create]) { newProject =>
        onSuccess(
          ProjectDao
            .insertProject(newProject, user)
            .transact(xa)
            .unsafeToFuture) { project =>
          complete(StatusCodes.Created, project)
        }
      }
    }
  }
```

#### Advantages

- easy to add new scopes as needed
- minimal effect on current data-models -- requires one additional field on `User`

#### Disadvantages

- lack of constraints make it possible to have conflicting rules which would necessitate some programmatic resolution approach
- requires some machinery to encode/decode scopes to/from the DB

### The Civilized Solution

This solution is more constrained in order to solve the conflicting rules problem.

- Each user has a series of columns representing object types for which there are scopes (i.e. columns named `projectScope` and `sceneScope`) where the value of the column is a scope type such as `READONLY`. This column would only accept a single value, which means that for each object type, a user can only have a single scope. This would prevent rule conflicts.
- Scope types would be defined within a `ScopeType` to enforce values
- Each API endpoint that is affected would do a lookup on the requesting user and see if there was a scope present that should prevent the request from being fulfilled.

Usage at the endpoint level might look like:

```scala
  def createProject: Route = authenticate { user =>
    authorizeAsync {
      UserDao
        .withoutScoping(ObjectType.Project, ScopeType.ReadOnly)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Project.Create]) { newProject =>
        onSuccess(
          ProjectDao
            .insertProject(newProject, user)
            .transact(xa)
            .unsafeToFuture) { project =>
          complete(StatusCodes.Created, project)
        }
      }
    }
  }
```

#### Advantages

- easy to add new scopes as needed
- a conflict prevention strategy is built-in
- promotes consistency by narrowing `ScopeType` members

#### Disadvantages

- requires a bunch of columns to be added (most likely to the `User` data-model/table)
- will require some machinery to map `ObjectType`s to columns

### The More Granular Variation

This solution is similar to _The Civilized Solution_ in that it would use an absence of scopes for a certain domain to imply full access. For example, if a user has no `project` scopes, then they are granted full access to project operations. This solution differs from the others in how the scopes are treated at the endpoint level. For example, if that user instead has a scope of `project:read`, they would be allowed to interact with project read end-points. This is in contrast to the other approaches that invert the process and determine what operations are _not_ allowed using scopes.

- could be implemented with a single scopes column or a column for each domain (project, scenes, etc.), where the columns would contain a list of applied scopes for that user
- Scope types would be defined within a `ScopeType` to enforce values
- Each API endpoint that is affected would do a lookup on the requesting user and ensure that if a scope is present for the relevant domain that the necessary scope was present

Usage at the endpoint level might look like:

```scala
  def createProject: Route = authenticate { user =>
    authorizeAsync {
      UserDao
        .hasScope(ObjectType.Project, ScopeType.Read)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Project.Create]) { newProject =>
        onSuccess(
          ProjectDao
            .insertProject(newProject, user)
            .transact(xa)
            .unsafeToFuture) { project =>
          complete(StatusCodes.Created, project)
        }
      }
    }
  }
```

#### Advantages

- easy to add new scopes as needed
- promotes consistency by narrowing `ScopeType` members
- encourages a more granular approach to scoping versus larger all-encompassing scopes types such as `readOnly`
- much more intuitive at the endpoint level

#### Disadvantages

- could require a bunch of columns to be added or...
- could alternatively require a more complex encoding/decoding process

## Decision

The lack of flexibility is enough to discard _The Basic Solution_ and the need for conflict resolution is probably enough to discard _The Wild West_ solution. _The Civilized Solution_ is less clear than _The More Granular Variation_ and they both seems to require the same amount of effort to implement.
We should go forward using _The More Granular Variation_.
