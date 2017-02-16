package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait ToolCategoryFields { self: Table[_] =>
  def slugLabel: Rep[String]
  def category: Rep[String]
}
