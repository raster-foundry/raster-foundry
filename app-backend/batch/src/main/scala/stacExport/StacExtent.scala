package com.rasterfoundry.batch.stacExport

// Spatial = bbox[lowerleftx, lowerlefty, upperrightx, upperrighty]
// temportal = ["start date isostring"||null, "end date isostring"||null]
// https://github.com/radiantearth/stac-spec/blob/dev/collection-spec/collection-spec.md#extent-object
case class StacExtent(spatial: List[Double], temporal: List[Option[String]])
