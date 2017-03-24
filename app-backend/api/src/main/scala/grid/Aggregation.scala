package com.azavea.rf.api.grid

import geotrellis.proj4._
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, Feature, Point, Polygon}
import geotrellis.vector.io.json._

// Code adapted from http://stackoverflow.com/questions/23457916/how-to-get-latitude-and-longitude-bounds-from-google-maps-x-y-and-zoom-parameter
object Aggregation {
  private val tileSize = 256.0

  case class TileCoordinates(z: Int, x: Int, y: Int) {
    lazy val children: Seq[TileCoordinates] =
      Seq(
        TileCoordinates(z + 1, x * 2, y * 2),
        TileCoordinates(z + 1, x * 2 + 1, y * 2),
        TileCoordinates(z + 1, x * 2 + 1, y * 2 + 1),
        TileCoordinates(z + 1, x * 2, y * 2 + 1)
      )

    lazy val childrenTileBounds: Seq[Projected[Polygon]] = {
      children.map(getTileBounds)
    }
  }

  case class GridData(count: Int)

  def latLngBboxFromString(bbox: String): Projected[Polygon] = try {
    val polygon = Extent.fromString(bbox).toPolygon()
    Projected(polygon, 4326).reproject(LatLng, WebMercator)(3857)
  } catch {
    case e: Exception => throw new IllegalArgumentException(
      "Four comma separated coordinates must be given for a bounding box"
    ).initCause(e)
  }

  // create the grid from a bounding box
  def bboxToGrid(bbox: String, zoom: Int): Seq[Projected[Polygon]] = {
    val extent = Extent.fromString(bbox)
    val se = extent.min
    val nw = extent.max
    val seTile = this.getTileAtLatLng(se, zoom)
    val nwTile = this.getTileAtLatLng(nw, zoom)
    val tiles = for(
      x <- seTile.x until (nwTile.x + 1);
      y <- nwTile.y until (seTile.y + 1)
    ) yield (TileCoordinates(z=zoom, x=x, y=y))
    tiles.map(
      tileCoords => {
        this.getTileBounds(tileCoords)
      }
    )
  }

  def fromLatLngToPoint(lngLatCoords: Point): Point = {
    val siny = Math.min(Math.max(Math.sin(lngLatCoords.y * (Math.PI / 180)), -.9999), .9999)

    Point(
      x=(128.0 + lngLatCoords.x * (tileSize/360.0)),
      y=(128.0 + 0.5 * Math.log((1+siny) / (1-siny)) * -(tileSize / (2 * Math.PI)))
    )
  }

  def fromPointToLatLng(pointCoords: Point): Point = {
    Point(
      x=((pointCoords.x - 128.0) / (tileSize/360.0)),
      y=(
        (2 *
           Math.atan(
             Math.exp(
               (pointCoords.y - 128.0) / -(tileSize / (2 * Math.PI)))
           ) - Math.PI / 2.0
        ) / (Math.PI / 180.0)
      )
    )
  }

  def getTileAtLatLng(lngLatCoords: Point, zoom: Int): TileCoordinates = {
    val scale = tileSize / (1<<zoom)
    val point = this.fromLatLngToPoint(lngLatCoords)
    TileCoordinates(x=Math.floor(point.x/scale).toInt, y=Math.floor(point.y/scale).toInt, z=zoom)
  }

  def getTileBounds(tile: TileCoordinates) = {
    val normalizedTile = this.normalizeTile(tile)
    val scale = tileSize / (1<<tile.z)
    val se = this.fromPointToLatLng(
      Point(x = normalizedTile.x.toDouble * scale, y = normalizedTile.y.toDouble * scale)
    )
    val nw = this.fromPointToLatLng(
      Point(
        x = normalizedTile.x.toDouble * scale + scale,
        y = normalizedTile.y.toDouble * scale + scale
      )
    )

    val polygon = Extent(se.x, nw.y, nw.x, se.y).toPolygon()
    Projected(polygon, 4326).reproject(LatLng, WebMercator)(3857)
  }

  def normalizeTile(tile: TileCoordinates): TileCoordinates = {
    val scale = 1<<tile.z
    val x = ((tile.x % scale) + scale) % scale
    val y = ((tile.y % scale) + scale) % scale
    TileCoordinates(x=x.toInt, y=y.toInt, z=tile.z)
  }
}
