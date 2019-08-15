package com.rasterfoundry.batch.stacExport

import io.circe.Encoder

// Spatial = bbox[lowerleftx, lowerlefty, upperrightx, upperrighty]
// temportal = ["start date isostring"||null, "end date isostring"||null]
// https://github.com/radiantearth/stac-spec/blob/dev/collection-spec/collection-spec.md#extent-object
case class StacExtent(spatial: List[Double], temporal: List[Option[String]])

object StacExtent {
  implicit val encStacExtent: Encoder[StacExtent] =
    Encoder.forProduct2("spatial", "temporal")(ext => {
      val spatial: List[Double] = ext.spatial
      val temporal: List[String] = ext.temporal.map(_.getOrElse("null"))
      (spatial, temporal)
    })
}
