package com.rasterfoundry.database

import geotrellis.layer.ZoomedLayoutScheme
import geotrellis.proj4.WebMercator

object tiling {
  val tmsLevels = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray
}
