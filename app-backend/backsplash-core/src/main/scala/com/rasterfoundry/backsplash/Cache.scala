package com.rasterfoundry.backsplash

import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.histogram.Histogram
import geotrellis.vector.MultiPolygon
import org.locationtech.spatial4j.io.GeohashUtils
import scalacache._
import scalacache.caffeine._

object Cache extends LazyLogging {

  class BacksplashCacheKeyBuilder extends CacheKeyBuilder {
    def toCacheKey(parts: Seq[Any]): String = {
      val strs = parts flatMap {
        // Can't match on Option[MultiPolygon] directly since the type parameter
        // gets eliminated
        case opt: Option[_] =>
          opt flatMap {
            case mp: MultiPolygon =>
              val centroid = mp.getCentroid()
              Some(GeohashUtils.encodeLatLon(centroid.getX(), centroid.getY(), 15))
            case x => Some(x.toString)
          }
        case part => Some(part.toString)
      }
      strs.mkString(":")
    }

    def stringToCacheKey(key: String) = key
  }

  val histCache: Cache[Array[Histogram[Double]]] =
    CaffeineCache[Array[Histogram[Double]]]

  val histCacheFlags =
    Flags(Config.cache.histogramCacheEnable, Config.cache.histogramCacheEnable)
  logger.info(s"Histogram Cache Status: ${histCacheFlags}")
}
