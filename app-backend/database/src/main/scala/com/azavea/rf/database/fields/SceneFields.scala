package com.azavea.rf.database.fields

import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel.{JobStatus, IngestStatus}
import geotrellis.slick.Projected
import geotrellis.vector.Geometry

import io.circe.Json

trait SceneFields  { self: Table[_] =>
  def name: Rep[String]
  def datasource: Rep[UUID]
  def sceneMetadata: Rep[Json]
  def cloudCover: Rep[Option[Float]]
  def acquisitionDate: Rep[Option[java.sql.Timestamp]]
  def thumbnailStatus: Rep[JobStatus]
  def boundaryStatus: Rep[JobStatus]
  def sunAzimuth: Rep[Option[Float]]
  def sunElevation: Rep[Option[Float]]
  def tileFootprint: Rep[Option[Projected[Geometry]]]
  def dataFootprint: Rep[Option[Projected[Geometry]]]
  def ingestLocation: Rep[Option[String]]
  def ingestStatus: Rep[IngestStatus]
}
