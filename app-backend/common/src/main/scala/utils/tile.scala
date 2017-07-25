package com.azavea.rf.common.utils

import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, Point, Polygon}


object TileUtils extends LazyLogging {
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

  def fromPointToLatLng(pointCoords: Point): Point = {
    Point(
      x = (pointCoords.x - 128.0) / (tileSize / 360.0),
      y = (2 * math.atan(math.exp((pointCoords.y - 128.0) / - ( tileSize / (2 * math.Pi)))) - math.Pi / 2.0) / (math.Pi / 180.0)
    )
  }

  def fromLatLngToPoint(lngLatCoords: Point): Point = {
    val sinY = math.min(math.max(math.sin(lngLatCoords.y * (math.Pi / 180)), -.9999), .9999)

    Point(
      x = 128.0 + lngLatCoords.x * (tileSize/360.0),
      y = 128.0 + 0.5 * math.log((1+sinY) / (1-sinY)) * -(tileSize / (2 * math.Pi))
    )
  }

  def normalizeTile(tile: TileCoordinates): TileCoordinates = {
    val scale = math.pow(2, tile.z)
    val x = ((tile.x % scale) + scale) % scale
    val y = ((tile.y % scale) + scale) % scale
    TileCoordinates(x=x.toInt, y=y.toInt, z=tile.z)
  }

  def getTileBounds(z: Int, x: Int, y: Int): Projected[Polygon] = {
    getTileBounds(TileCoordinates(z, x, y))
  }

  def getTileBounds(tile: TileCoordinates): Projected[Polygon] = {
    val normalizedTile = this.normalizeTile(tile)
    val scale = tileSize / math.pow(2, tile.z)
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

  def getTileAtLatLng(lngLatCoords: Point, zoom: Int): TileCoordinates = {
    val scale = tileSize / math.pow(2, zoom)
    val point = this.fromLatLngToPoint(lngLatCoords)
    TileCoordinates(x=math.floor(point.x/scale).toInt, y=math.floor(point.y/scale).toInt, z=zoom)
  }
}
