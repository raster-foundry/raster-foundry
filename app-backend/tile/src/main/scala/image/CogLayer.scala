package com.azavea.rf.tile.image

import com.azavea.rf.common.utils.RangeReaderUtils
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
    for {
      rr <- RangeReaderUtils.fromUri(uri)
      tiff = GeoTiffReader.readMultiband(rr, decompress = false, streaming = true)
      transform = Proj4Transform(tiff.crs, WebMercator)
      inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      tmsTileExtent = TmsLevels(z).mapTransform.keyToExtent(x, y)
      tmsTileRE = RasterExtent(tmsTileExtent, TmsLevels(z).cellSize)
      tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)
      raster <- Try(tiff.crop(tiffTileRE.extent, tiffTileRE.cellSize, ResampleMethod.DEFAULT, AutoHigherResolution)).toOption      
    } yield raster.reproject(tmsTileRE, transform, inverseTransform).tile
  }

}

