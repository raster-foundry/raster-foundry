package com.rasterfoundry

import cats._
import cats.data._
import cats.implicits._
import geotrellis.layer.SpatialKey
import geotrellis.raster.split._
import geotrellis.raster.{CellSize, MultibandTile}
import geotrellis.util.Component
import geotrellis.vector._

import scala.concurrent.duration._
import scala.util.Either

package object batch {

  @SuppressWarnings(Array("all"))
  implicit class IdOps[A](a: A) {
    def unused: Unit = ()
  }

  implicit class HasCellSize[
      A <: {
        def rows: Int; def cols: Int; def extent: Extent
      }
  ](obj: A) {
    def cellSize: CellSize =
      CellSize(obj.extent.width / obj.cols, obj.extent.height / obj.rows)
  }

  implicit val rfSpatialKeyIntComponent =
    Component[(SpatialKey, Int), SpatialKey](
      from => from._1,
      (from, to) => (to, from._2)
    )

  implicit val rfProjectedExtentIntComponent =
    Component[(ProjectedExtent, Int), ProjectedExtent](
      from => from._1,
      (from, to) => (to, from._2)
    )

  @SuppressWarnings(Array("ClassNames"))
  implicit class withMultibandTileSplitMethods(val self: MultibandTile)
      extends MultibandTileSplitMethods

  /** Borrowed from Cats.
    * TODO: Use /their/ implementation once cats 1.0.0 comes out.
    */
  def fromOptionF[F[_], E, A](fopt: F[Option[A]], ifNone: => E)(
      implicit F: Functor[F]
  ): EitherT[F, E, A] =
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
