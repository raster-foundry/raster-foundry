package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected

import java.net.URI

trait ImageFields  { self: Table[_] =>
  def rawDataBytes: Rep[Int]
  def sourceUri: Rep[URI]
  def extent: Rep[Option[Projected[Geometry]]]
}
