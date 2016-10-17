package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel.User

trait UserToOrganizationFields { self: Table[_] =>
  def userId: Rep[String]
  def role: Rep[User.Role]
}
