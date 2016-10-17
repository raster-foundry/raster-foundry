package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel.Visibility

trait VisibilityField  { self: Table[_] =>
  val visibility: Rep[Visibility]
}