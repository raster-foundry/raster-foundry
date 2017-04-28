package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.query.UserQueryParameters
import com.azavea.rf.database.tables.Users
import com.azavea.rf.datamodel.{User, Visibility}
import slick.lifted.ForeignKeyQuery

trait UserFkFields { self: Table[_] =>
  def createdBy: Rep[String]
  def modifiedBy: Rep[String]
  def owner: Rep[String]

  def ownerUserFK: ForeignKeyQuery[Users, User]
  def createdByUserFK: ForeignKeyQuery[Users, User]
  def modifiedByUserFK: ForeignKeyQuery[Users, User]
}

object UserFkFields {
  implicit class DefaultQuery[M <: UserFkFields, U, C[_]](that: Query[M, U, Seq]) {
    def filterByUser(userParams: UserQueryParameters) = {
      that.filter{ rec =>
        List(
          userParams.createdBy.map(rec.createdBy === _),
          userParams.modifiedBy.map(rec.modifiedBy === _),
          userParams.owner.map(rec.owner === _)
        )
          .flatten
          .reduceLeftOption(_ && _)
          .getOrElse(true: Rep[Boolean])
      }
    }

    def filterToOwner(user: User) = {
      that.filter(_.owner === user.id)
    }

    def filterToOwnerIfNotInRootOrganization(user: User) = {
      if (!user.isInRootOrganization) {
        that.filter(_.owner === user.id)
      } else {
        that
      }
    }
  }
}
