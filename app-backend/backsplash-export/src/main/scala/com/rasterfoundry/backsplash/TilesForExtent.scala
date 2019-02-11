package com.rasterfoundry.backsplash.export

import geotrellis.vector.Extent

object TilesForExtent {
  // generate a list of tiles that fall under the provided latlng extent
  def latLng(extent: Extent, zoom: Int): List[(Int, Int)] = {
    val blLat = extent.ymax
    val blLng = extent.xmin

    val trLat = extent.ymin
    val trLng = extent.xmax

    val (minX, minY) = getTileXY(blLat, blLng, zoom)
    val (maxX, maxY) = getTileXY(trLat, trLng, zoom)

    for {
      xs <- minX to maxX
      ys <- minY to maxY
    } yield (xs, ys)
  }.toList
}
