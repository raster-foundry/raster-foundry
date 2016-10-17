package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait ImageFields  { self: Table[_] =>
  def rawDataBytes: Rep[Int]
  def filename: Rep[String]
  def sourceuri: Rep[String]
}
