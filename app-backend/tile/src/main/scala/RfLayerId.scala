package com.azavea.rf.tile

import java.util.UUID

import geotrellis.spark.LayerId

/**
  * RasterFoundry layers belong to user and organization.
  * Eventually these must be mapped to an S3 prefix.
  * This class encapsulates the relationship and gives a place to modify the mapping
  */
case class RfLayerId(org: UUID, user: String, scene: UUID) {
  def prefix: String = s"${org.toString}/$user"
  def catalogId(zoom: Int) = LayerId(scene.toString, zoom)
}


