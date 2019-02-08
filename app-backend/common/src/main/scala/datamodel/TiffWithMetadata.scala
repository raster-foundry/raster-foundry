package com.rasterfoundry.common.datamodel
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.tags.TiffTags

final case class TiffWithMetadata(
    tiff: GeoTiff[MultibandTile],
    tiffTags: TiffTags
)
