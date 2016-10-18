package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._

import java.net.URI

trait ImageFields  { self: Table[_] =>
  def rawDataBytes: Rep[Int]
  def filename: Rep[String]
  def sourceuri: Rep[URI]
}
