package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait ToolRunFields { self: Table[_] =>
  def name: Rep[Option[String]]
}
