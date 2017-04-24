package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.query.UserQueryParameters
import com.azavea.rf.database.tables.Users
import com.azavea.rf.datamodel.{User, Visibility}
import slick.lifted.ForeignKeyQuery


trait UserFkVisibleFields extends UserFkFields with VisibilityField { self: Table[_] => }

object UserFkVisibleFields {
  implicit class DefaultQuery[M <: UserFkVisibleFields, U, C[_]](that: Query[M, U, Seq]) {
    def filterUserVisibility(user: User) = {
      that.filter { rec => {
        rec.visibility === Visibility.fromString("PUBLIC") ||
        rec.owner === user.id ||
        user.isInRootOrganization }
      }
    }
  }
}
