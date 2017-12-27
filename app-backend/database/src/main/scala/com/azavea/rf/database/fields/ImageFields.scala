package com.azavea.rf.database.fields

import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait ImageFields  { self: Table[_] =>
  def id: Rep[UUID]
  def rawDataBytes: Rep[Long]
  def filename: Rep[String]
  def sourceuri: Rep[String]
}
