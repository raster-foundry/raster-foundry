package com.rasterfoundry.batch.stacExport

import io.circe._
import io.circe.generic.semiauto._
import geotrellis.server.stac._
import geotrellis.server.stac.Implicits._

// Spatial = bbox[lowerleftx, lowerlefty, upperrightx, upperrighty]
// temportal = ["start date isostring"||null, "end date isostring"||null]
// https://github.com/radiantearth/stac-spec/blob/dev/collection-spec/collection-spec.md#extent-object
case class StacExtent(spatial: SpatialExtent, temporal: TemporalExtent)

case class SpatialExtent(bbox: List[Bbox])

case class TemporalExtent(interval: List[List[Option[String]]])

object StacExtent {
  implicit val encStacExtent: Encoder[StacExtent] = deriveEncoder[StacExtent]
}

object SpatialExtent {
  implicit val encSpatialExtent: Encoder[SpatialExtent] =
    deriveEncoder[SpatialExtent]
}

object TemporalExtent {
  implicit val encTemporalExtent: Encoder[TemporalExtent] =
    deriveEncoder[TemporalExtent]
}
