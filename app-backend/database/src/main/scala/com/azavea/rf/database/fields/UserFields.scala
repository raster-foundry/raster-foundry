package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel.UserRole

trait UserFields { self: Table[_] =>
  def id: Rep[String]
  def role: Rep[UserRole]
}
