package com.azavea.rf.user

import scala.concurrent.{Future, ExecutionContext}
import java.sql.Timestamp

import com.azavea.rf.datamodel.latest.schema.tables.Users
import com.azavea.rf.datamodel.latest.schema.tables.UsersRow
import com.azavea.rf.utils.Database

case class UsersRowCreate(
  isActive: Option[Boolean] = Some(true), isStaff: Option[Boolean] = Some(false),
  email: String, firstName: String, lastName: String, organizationId: java.util.UUID
)

case class UsersRowApi(
  id: java.util.UUID, isActive: Option[Boolean] = Some(true),
  isStaff: Option[Boolean] = Some(false), email: String, firstName: String,
  lastName: String, organizationId: java.util.UUID
) {
  def this(user: UsersRow) = this(
    id=user.id,
    isActive=user.isActive,
    isStaff=user.isStaff,
    email=user.email,
    firstName=user.firstName,
    lastName=user.lastName,
    organizationId=user.organizationId
  )
}

object UserService {

  def getUsers()(implicit database:Database, ec:ExecutionContext) = {
    import database.driver.api._

    database.db.run {
      Users.result
    } map {
      users => {
        users.map(user => new UsersRowApi(user))
      }
    }
  }

  def getUserById(id: java.util.UUID)(implicit database:Database, ec:ExecutionContext) = {
    import database.driver.api._

    database.db.run {
      Users.filter(_.id === id).result.headOption
    } map {
      userOption => userOption match {
        case Some(user) => new UsersRowApi(user)
        case _ => {
          // catch this to return 404
          throw new Exception("404 Resource not found")
        }
      }
    }
  }

  def createUser(user:UsersRowCreate)(implicit database:Database, ec:ExecutionContext) = {
    import database.driver.api._
    val now = new Timestamp((new java.util.Date()).getTime())
    val newUUID = java.util.UUID.randomUUID
    val userRow = UsersRow.apply(
      id=newUUID,
      createdAt=now,
      modifiedAt=now,
      isActive=user.isActive,
      isStaff=user.isStaff,
      email=user.email,
      firstName=user.firstName,
      lastName=user.lastName,
      organizationId=user.organizationId
    )

    database.db.run {
      Users.forceInsert(userRow)
    } map {
      x => new UsersRowApi(userRow)
    }
  }

  def getUserByEmail(email:String)(implicit database:Database, ec:ExecutionContext):Future[Option[UsersRow]] = {
    import database.driver.api._

    database.db.run {
      Users.filter(_.email === email)
        .sortBy(_.createdAt)
        .take(1)
        .result
        .headOption
    }
  }

  def updateUser(user:UsersRowApi, id:java.util.UUID)(implicit database:Database, ec:ExecutionContext) = {
    import database.driver.api._
    val now = new Timestamp((new java.util.Date()).getTime())

    // ignores id if it's changed in the request
    // TODO throw exception if ignored attributes are changed
    val query = for { u <- Users if u.id === id} yield (
      u.isActive, u.isStaff, u.email, u.firstName, u.lastName, u.organizationId,
      u.modifiedAt
    )
    database.db.run {
      query.update(
        (
          user.isActive, user.isStaff, user.email, user.firstName,
          user.lastName, user.organizationId, now
        )
      )
    }
  }
}
