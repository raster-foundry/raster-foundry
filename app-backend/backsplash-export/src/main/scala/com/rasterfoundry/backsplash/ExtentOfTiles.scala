package com.rasterfoundry.backsplash.export

import geotrellis.spark.tiling._
import geotrellis.vector.Extent
import geotrellis.proj4._

object ExtentOfTiles {
  // Find the extent which covers the provided list of tile coordinates at the provided zoom
  def webMercator(tiles: List[(Int, Int)], zoom: Int): Extent = {
    val keyToExtent =
      ZoomedLayoutScheme(WebMercator, 256)
        .levelForZoom(zoom)
        .layout
        .mapTransform
        .keyToExtent _

    val xs = tiles.map(_._1)
    val ys = tiles.map(_._2)
    val blExtent = keyToExtent(xs.min, ys.max)
    val trExtent = keyToExtent(xs.max, ys.min)
    blExtent combine trExtent
  }

}
