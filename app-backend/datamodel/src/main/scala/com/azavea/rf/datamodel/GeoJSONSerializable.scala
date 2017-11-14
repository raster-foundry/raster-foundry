package com.azavea.rf.datamodel

import geotrellis.slick.Projected
import geotrellis.vector.Geometry

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
