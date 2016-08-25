package com.azavea.rf.user

import scala.concurrent.ExecutionContext

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

  def getUserById(implicit database:Database, ec:ExecutionContext, id: java.util.UUID) = {
    import database.driver.api._

    database.db.run {
      Users.filter(_.id === id).result.headOption
    }
  }

  def createUser(implicit database:Database, ec:ExecutionContext, user:UsersRow ) = {
    import database.driver.api._
    database.db.run {
      Users.forceInsert(user)
    }
  }

  def updateUser(implicit database:Database, ec:ExecutionContext, user:UsersRow ) = {
    import database.driver.api._
    database.db.run {
      Users.filter(_.id === user.id).update(user)
    }
  }
}
