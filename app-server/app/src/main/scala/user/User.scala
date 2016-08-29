package com.azavea.rf.user

import scala.concurrent.{Future, ExecutionContext}

import com.azavea.rf.datamodel.latest.schema.tables.Users
import com.azavea.rf.datamodel.latest.schema.tables.UsersRow
import com.azavea.rf.utils.Database

object UserService {
  def getUsers()(implicit database:Database, ec:ExecutionContext) = {
    import database.driver.api._

    database.db.run {
      Users.result
    }
  }

  def getUserById(id: java.util.UUID)(implicit database:Database, ec:ExecutionContext) = {
    import database.driver.api._

    database.db.run {
      Users.filter(_.id === id).result.headOption
    }
  }

  def createUser(user:UsersRow)(implicit database:Database, ec:ExecutionContext) = {
    import database.driver.api._

    database.db.run {
      Users.forceInsert(user)
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

  def updateUser(user:UsersRow)(implicit database:Database, ec:ExecutionContext) = {
    import database.driver.api._

    database.db.run {
      Users.filter(_.id === user.id).update(user)
    }
  }
}
