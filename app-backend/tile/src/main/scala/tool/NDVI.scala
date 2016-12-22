package com.azavea.rf.tile.tool

import geotrellis.raster._
import geotrellis.raster.render.{ColorRamp, ColorMap}

/**
  * Normalized Difference Vegetation Index
  * Assumes that input MultibandTile has the following bands:
  *
  * Band 0 = Red
  * Band 1 = Near Infrared
  */
object NDVI extends (MultibandTile => Tile) {
  val colorRamp = ColorRamp(Array[Int](0xffffe5aa, 0xf7fcb9ff, 0xd9f0a3ff, 0xaddd8eff, 0x78c679ff, 0x41ab5dff, 0x238443ff, 0x006837ff, 0x004529ff))
  val breaks = Array[Double](0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0)

  def colorMap(b: Option[Array[Double]], r: Option[ColorRamp]): ColorMap =
    ColorMap(b.getOrElse(breaks), r.getOrElse(colorRamp))

  def apply(tile: MultibandTile): Tile = {
    tile.interpretAs(DoubleCellType).combineDouble(0, 1) { (r, nir) => (nir - r) / (nir + r) }
  }

  def tool(bands: Map[Symbol, Op]): Map[Symbol, Op] =
    for {
      red <- bands.get('red)
      nir <- bands.get('nir)
    } yield {
      val num = red.combineDouble(nir)(_ - _)
      val den = red.combineDouble(nir)(_ + _)
      Map('ndvi -> num.combineDouble(den)(_ / _))
    }
}
