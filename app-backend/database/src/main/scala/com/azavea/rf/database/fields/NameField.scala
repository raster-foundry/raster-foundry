package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait NameField  { self: Table[_] =>
  def name: Rep[String]
}
