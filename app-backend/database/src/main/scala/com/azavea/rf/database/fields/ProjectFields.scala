package com.azavea.rf.database.fields

import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait ProjectFields  { self: Table[_] =>
  def id: Rep[UUID]
  def name: Rep[String]
  def slugLabel: Rep[String]
  def description: Rep[String]
}
