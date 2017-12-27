package com.azavea.rf.database.fields

import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait ToolRunFields { self: Table[_] =>
  def id: Rep[UUID]
  def name: Rep[Option[String]]
}
