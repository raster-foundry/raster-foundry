package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel.JobStatus
import geotrellis.slick.Projected
import geotrellis.vector.Geometry

trait SceneFields  { self: Table[_] =>
  def name: Rep[String]
  def resolutionMeters: Rep[Float]
  def datasource: Rep[String]
  def sceneMetadata: Rep[Map[String, Any]]
  def cloudCover: Rep[Option[Float]]
  def acquisitionDate: Rep[Option[java.sql.Timestamp]]
  def thumbnailStatus: Rep[JobStatus]
  def boundaryStatus: Rep[JobStatus]
  def status: Rep[JobStatus]
  def sunAzimuth: Rep[Option[Float]]
  def sunElevation: Rep[Option[Float]]
  def footprint: Rep[Option[Projected[Geometry]]]
}
