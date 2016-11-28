package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.query.UserQueryParameters
import com.azavea.rf.database.tables.Users
import com.azavea.rf.datamodel.User
import slick.lifted.ForeignKeyQuery

trait UserFkFields { self: Table[_] =>
  def createdBy: Rep[String]
  def modifiedBy: Rep[String]

  def createdByUserFK: ForeignKeyQuery[Users, User]
  def modifiedByUserFK: ForeignKeyQuery[Users, User]
}

object UserFkFields {
  implicit class DefaultQuery[M <: UserFkFields, U, C[_]](that: Query[M, U, Seq]) {
    def filterByUser(userParams: UserQueryParameters) = {
      that.filter{ rec =>
        List(
          userParams.createdBy.map(rec.createdBy === _),
          userParams.modifiedBy.map(rec.modifiedBy === _)
        )
          .flatten
          .reduceLeftOption(_ && _)
          .getOrElse(true: Rep[Boolean])
      }
    }

    def filterToOwner(user: User) = {
      that.filter(_.createdBy === user.id)
    }
  }
}
