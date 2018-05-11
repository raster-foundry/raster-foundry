package com.azavea.rf.tile.image

import com.azavea.rf.common.utils._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.resample._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.proj4._
import geotrellis.spark.tiling._
import scala.util.Properties
import scala.math
import scala.util.Try

object CogLayer {
  private val TmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  def fetch(uri: String, z: Int, x: Int, y: Int): Option[MultibandTile] = {
    RangeReaderUtils.fromUri(uri).flatMap { rr =>
      val tiff = GeoTiffReader.readMultiband(rr, decompress = false, streaming = true)
      val transform = Proj4Transform(tiff.crs, WebMercator)
      val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      val tmsTileRE = RasterExtent(
        extent = TmsLevels(z).mapTransform.keyToExtent(x, y),
        cols = 256, rows = 256)
      val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)

      if (tiffTileRE.extent.intersects(tiff.extent)) {
        val overview = GeoTiffUtils.closestTiffOverview(tiff, tiffTileRE.cellSize, Auto(0))
        val raster = GeoTiffUtils.cropGeoTiff(overview, tiffTileRE.extent)
        Some(raster.tile)
      } else None
    }
  }
}

