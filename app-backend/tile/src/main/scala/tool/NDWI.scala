package com.azavea.rf.tile.tool

import geotrellis.raster._
import geotrellis.raster.render.{ColorRamp, ColorMap}

/**
  * Normalized Difference Water Index
  * Assumes that input MultibandTile has the following bands:
  *
  * Band 0 = Green
  * Band 1 = Near Infrared
  */
object NDWI extends (MultibandTile => Tile) {
  val colorRamp = ColorRamp(
    Array[Int](0xaacdff44, 0x70abffff, 0x3086ffff, 0x1269e2ff, 0x094aa5ff,
      0x012c69ff))
  val breaks = Array[Double](0, 0.1, 0.2, 0.3, 0.4, 1.0)

  def colorMap(b: Option[Array[Double]], r: Option[ColorRamp]): ColorMap =
    ColorMap(b.getOrElse(breaks), r.getOrElse(colorRamp))

  def apply(tile: MultibandTile): Tile = {
    tile.convert(DoubleCellType).combineDouble(0, 1) { (g, nir) =>
      (g - nir) / (g + nir)
    }
  }
}
