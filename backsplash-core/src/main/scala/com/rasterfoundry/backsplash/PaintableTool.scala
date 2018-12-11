package com.rasterfoundry.backsplash

import com.azavea.maml.ast.Expression
import com.azavea.maml.error.Interpreted
import com.azavea.maml.eval.BufferingInterpreter
import geotrellis.raster.histogram.Histogram
import geotrellis.raster._
import geotrellis.vector._
import geotrellis.server._
import cats.effect._
import cats.implicits._

sealed trait PaintableTool {
  def tms(z: Int, x: Int, y: Int)(
      implicit cs: ContextShift[IO]): IO[Interpreted[MultibandTile]]
  def extent(extent: Extent, cellsize: CellSize)(
      implicit cs: ContextShift[IO]): IO[Interpreted[MultibandTile]]
  def histogram(maxCellsSampled: Int)(
      implicit cs: ContextShift[IO]): IO[Interpreted[List[Histogram[Double]]]]
}

object PaintableTool {
  def apply[Param: TmsReification: ExtentReification: HasRasterExtents](
      expr: Expression,
      painter: MultibandTile => MultibandTile,
      paramMap: Map[String, Param],
      interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT,
      paint: Boolean // whether to paint the tile or return raw values
  ): PaintableTool = new PaintableTool {

    def tms(z: Int, x: Int, y: Int)(
        implicit cs: ContextShift[IO]): IO[Interpreted[MultibandTile]] = {
      val eval = LayerTms(IO.pure(expr), IO.pure(paramMap), interpreter)
      val raw = eval(z, x, y)
      if (paint) {
        raw.map({ validated =>
          validated.map(painter(_))
        })
      } else {
        raw
      }
    }

    def extent(extent: Extent, cellsize: CellSize)(
        implicit cs: ContextShift[IO]): IO[Interpreted[MultibandTile]] = {
      val eval = LayerExtent(IO.pure(expr), IO.pure(paramMap), interpreter)
      val raw = eval(extent, cellsize)
      if (paint) {
        raw.map({ validated =>
          validated.map(painter(_))
        })
      } else {
        raw
      }
    }

    def histogram(maxCellsSampled: Int)(implicit cs: ContextShift[IO])
      : IO[Interpreted[List[Histogram[Double]]]] =
      LayerHistogram(IO.pure(expr),
                     IO.pure(paramMap),
                     interpreter,
                     maxCellsSampled)
  }

}
