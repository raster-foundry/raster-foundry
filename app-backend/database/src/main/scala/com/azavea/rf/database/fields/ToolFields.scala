package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait ToolFields { self: Table[_] =>
  def title: Rep[String]
}
