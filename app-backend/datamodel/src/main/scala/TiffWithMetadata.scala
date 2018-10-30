package com.rasterfoundry.datamodel
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.tags.TiffTags

case class TiffWithMetadata(
    tiff: GeoTiff[MultibandTile],
    tiffTags: TiffTags
)
