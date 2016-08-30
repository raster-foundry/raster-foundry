package com.azavea.rf.user

import scala.concurrent.{Future, ExecutionContext}
import java.sql.Timestamp

import com.azavea.rf.datamodel.latest.schema.tables.Users
import com.azavea.rf.datamodel.latest.schema.tables.UsersRow
import com.azavea.rf.utils.Database

case class UsersRowCreate(
  isActive: Option[Boolean] = Some(true), isStaff: Option[Boolean] = Some(false),
  email: String, firstName: String, lastName: String, organizationId: java.util.UUID
) {
  def toUsersRow(): UsersRow = {
    val now = new Timestamp((new java.util.Date()).getTime())
    val newUUID = java.util.UUID.randomUUID
    UsersRow.apply(
      id=newUUID,
      createdAt=now,
      modifiedAt=now,
      isActive=this.isActive,
      isStaff=this.isStaff,
      email=this.email,
      firstName=this.firstName,
      lastName=this.lastName,
      organizationId=this.organizationId
    )
  }
}

object UserService {

  def getUsers()(implicit database:Database, ec:ExecutionContext): Future[Seq[UsersRow]] = {
    import database.driver.api._

    database.db.run {
      Users.result
    }
  }

  def getUserById(id: java.util.UUID)
                 (implicit database:Database, ec:ExecutionContext): Future[Option[UsersRow]] = {
    import database.driver.api._

    database.db.run {
      Users.filter(_.id === id).result.headOption
    }
  }

  def createUser(user:UsersRowCreate)
                (implicit database:Database, ec:ExecutionContext): Future[UsersRow] = {
    import database.driver.api._
    val userRow = user.toUsersRow()

    database.db.run {
      Users.forceInsert(userRow)
    } map {
      x => userRow
    }
  }

  def getUserByEmail(email:String)
                    (implicit database:Database, ec:ExecutionContext): Future[Option[UsersRow]] = {
    import database.driver.api._

    database.db.run {
      Users.filter(_.email === email)
        .sortBy(_.createdAt)
        .take(1)
        .result
        .headOption
    }
  }

  def updateUser(user:UsersRow, id:java.util.UUID)
                (implicit database:Database, ec:ExecutionContext): Future[Int] = {
    import database.driver.api._
    val now = new Timestamp((new java.util.Date()).getTime())

    // ignores id if it's changed in the request
    // TODO throw exception if ignored attributes are changed
    val query = for { u <- Users if u.id === id} yield (
      u.isActive, u.isStaff, u.email, u.firstName, u.lastName, u.organizationId,
      u.modifiedAt
    )
    // TODO catch exception when uniqueness constraint on email fails
    // should return 400 bad request : email is already in use
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
