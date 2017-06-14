package com.azavea.rf

import geotrellis.raster.split._
import geotrellis.raster.{CellSize, MultibandTile}
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.util.Component
import geotrellis.vector._
import cats._
import cats.data._
import cats.implicits._

import scala.concurrent.duration.Duration
import scala.util.Either

package object batch {
  implicit class HasCellSize[A <: { def rows: Int; def cols: Int; def extent: Extent }](obj: A) {
    def cellSize: CellSize = CellSize(obj.extent.width / obj.cols, obj.extent.height / obj.rows)
  }

  implicit class withRasterFoundryTilerKeyMethods(val self: (ProjectedExtent, Int))
      extends TilerKeyMethods[(ProjectedExtent, Int), (SpatialKey, Int)] {
    def extent = self._1.extent
    def translate(spatialKey: SpatialKey) = (spatialKey, self._2)
  }

  implicit val rfSpatialKeyIntComponent =
    Component[(SpatialKey, Int), SpatialKey](from => from._1, (from, to) => (to, from._2))

  implicit val rfProjectedExtentIntComponent =
    Component[(ProjectedExtent, Int), ProjectedExtent](from => from._1, (from, to) => (to, from._2))

  implicit class withMultibandTileSplitMethods(val self: MultibandTile) extends MultibandTileSplitMethods

  /** Borrowed from Cats.
    * TODO: Use /their/ implementation once cats 1.0.0 comes out.
    */
  def fromOptionF[F[_], E, A](fopt: F[Option[A]], ifNone: => E)(implicit F: Functor[F]): EitherT[F, E, A] =
    EitherT(F.map(fopt)(opt => Either.fromOption(opt, ifNone)))

  def retry[A](time: Duration, pause: Duration)(code: => A): A = {
    var result: Option[A] = None
    var remaining = time
    while (remaining > Duration.Zero) {
      remaining -= pause
      try {
        result = Some(code)
        remaining = Duration.Zero
      } catch {
        case _ if remaining > Duration.Zero => Thread.sleep(pause.toMillis)
      }
    }
    result.getOrElse(throw new Exception(s"Retry failed in $time"))
  }
}
