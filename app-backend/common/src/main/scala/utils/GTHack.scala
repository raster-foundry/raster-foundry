package geotrellis.hack

import geotrellis.raster._
import geotrellis.raster.io.geotiff._

object GTHack {
  def closestTiffOverview[T <: CellGrid](
      tiff: GeoTiff[T],
      cs: CellSize,
      strategy: OverviewStrategy
  ): GeoTiff[T] = getClosestOverviewHack(tiff, cs, strategy)

  // https://github.com/locationtech/geotrellis/pull/2802
  def getClosestOverviewHack[T <: CellGrid](
      tiff: GeoTiff[T],
      cellSize: CellSize,
      strategy: OverviewStrategy
  ): GeoTiff[T] = {
    tiff.overviews match {
      case Nil => tiff
      case list =>
        strategy match {
          case AutoHigherResolution =>
            (tiff :: list) // overviews can have erased extent information
              .map { v =>
                (cellSize.resolution - v.cellSize.resolution) -> v
              }
              .filter(_._1 >= 0)
              .sortBy(_._1)
              .map(_._2)
              .headOption
              .getOrElse(tiff)
          case Auto(n) =>
            (tiff :: list)
              .sortBy(v =>
                math.abs(v.cellSize.resolution - cellSize.resolution))
              .lift(n)
              .getOrElse(tiff) // n can be out of bounds,
          // makes only overview lookup as overview position is important
          case Base => tiff
        }
    }
  }
}
