package com.azavea.rf.datamodel

import geotrellis.vector.{Geometry, Projected}

import io.circe.generic.JsonCodec

trait GeoJSONFeature {
  val id: Any
  val properties: Any
  val _type: String
  val geometry: Option[Projected[Geometry]]
}

trait GeoJSONSerializable[T <: GeoJSONFeature] {
  def toGeoJSONFeature: T
}
