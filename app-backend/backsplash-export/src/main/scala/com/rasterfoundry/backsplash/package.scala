package com.rasterfoundry.backsplash

import com.typesafe.scalalogging.LazyLogging
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.contrib.vlm.geotiff._
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling._
import geotrellis.vector.{Extent, Point}

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

package object export extends LazyLogging {

  private val tileSize = ExportConfig.Export.tileSize

  def getTileXY(lat: Double,
                lng: Double,
                zoom: Int,
                displayProjection: CRS = WebMercator): (Int, Int) = {
    val p = Point(lng, lat).reproject(LatLng, displayProjection)

    val webMercator = ZoomedLayoutScheme(WebMercator, 256)
    val cellSize = webMercator.levelForZoom(zoom).layout.cellSize

    val SpatialKey(x, y) = FloatingLayoutScheme(tileSize)
      .levelFor(WebMercator.worldExtent, cellSize)
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

    val tileCols = minTileCol - maxTileCol + 1
    val tileRows = minTileRow - maxTileRow + 1
    logger.info(s"Columns: ${tileCols}, Rows: ${tileRows}")
    val tileLayout =
      TileLayout(tileCols, tileRows, tileSize, tileSize)
    GeoTiffSegmentLayout(
      tileCols * tileSize,
      tileRows * tileSize,
      tileLayout,
      Tiled(tileSize, tileSize),
      PixelInterleave
    )
  }

  def getRasterSource(uri: String): RasterSource = {
    val enableGDAL = ExportConfig.RasterSource.enableGDAL
    if (enableGDAL) {
      GDALRasterSource(
        URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()))
    } else {
      new GeoTiffRasterSource(uri)
    }

  }
}
