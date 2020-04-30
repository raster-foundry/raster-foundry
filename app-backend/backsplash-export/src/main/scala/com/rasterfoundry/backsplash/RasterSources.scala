package com.rasterfoundry.backsplash.export

import geotrellis.raster.RasterSource

import scala.collection.mutable

object RasterSources {
  private val sources: mutable.Map[String, RasterSource] =
    mutable.Map[String, RasterSource]()

  def getOrUpdate(key: String) = sources.get(key) match {
    case Some(rs) =>
      rs
    case None =>
      val rs = getRasterSource(key)
      sources += (key -> rs)
      rs
  }
}
