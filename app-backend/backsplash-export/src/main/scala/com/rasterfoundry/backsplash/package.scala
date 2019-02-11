package com.rasterfoundry.backsplash

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling._
import geotrellis.proj4._
import geotrellis.vector.{Extent, Point}
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.gdal._

import java.net.{URLDecoder}
import java.nio.charset.StandardCharsets

import com.typesafe.scalalogging.LazyLogging

package object export extends LazyLogging {

  def getTileXY(lat: Double,
                lng: Double,
                zoom: Int,
                displayProjection: CRS = WebMercator): (Int, Int) = {
    val p = Point(lng, lat).reproject(LatLng, displayProjection)
    val SpatialKey(x, y) = ZoomedLayoutScheme(displayProjection)
      .levelForZoom(zoom)
      .layout
      .mapTransform(p)
    (x, y)
  }

  def exportSegmentLayout(extent: Extent, zoom: Int): GeoTiffSegmentLayout = {
    val (minTileCol, minTileRow) =
      getTileXY(extent.ymin, extent.xmax, zoom)
    val (maxTileCol, maxTileRow) =
      getTileXY(extent.ymax, extent.xmin, zoom)

    logger.info(s"Minimum Tile: ${maxTileCol}, ${maxTileRow}")
    logger.info(s"Maximum Tile: ${minTileCol}, ${minTileRow}")

    val tileCols = (minTileCol - maxTileCol + 1) * 256
    val tileRows = (minTileRow - maxTileRow + 1) * 256
    logger.info(s"Columns: ${tileCols}, Rows: ${tileRows}")
    val tileLayout =
      TileLayout(tileCols / 256, tileRows / 256, 256, 256)
    GeoTiffSegmentLayout(
      tileCols,
      tileRows,
      tileLayout,
      Tiled,
      PixelInterleave
    )
  }

  def getRasterSource(uri: String): RasterSource = {
    val enableGDAL = false // for now
    if (enableGDAL) {
      GDALRasterSource(
        URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()))
    } else {
      new GeoTiffRasterSource(uri)
    }
  }
}
