package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.backsplash.color.{Implicits => ColorImplicits}

import com.azavea.maml.ast.Expression
import com.azavea.maml.error.Interpreted
import com.azavea.maml.eval.BufferingInterpreter
import geotrellis.raster.histogram.Histogram
import geotrellis.raster._
import geotrellis.vector._
import geotrellis.server._
import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

sealed trait PaintableTool extends ColorImplicits with LazyLogging {
  def tms(z: Int, x: Int, y: Int)(
      implicit cs: ContextShift[IO]): IO[Interpreted[MultibandTile]]
  def extent(extent: Extent, cellsize: CellSize)(
      implicit cs: ContextShift[IO]): IO[Interpreted[MultibandTile]]
  def histogram(maxCellsSampled: Int)(
      implicit cs: ContextShift[IO]): IO[Interpreted[List[Histogram[Double]]]]

  def renderDefinition: Option[RenderDefinition]
}

object PaintableTool {
  def apply[Param: TmsReification: ExtentReification: HasRasterExtents](
      expr: Expression,
      paramMap: Map[String, Param],
      renderDef: Option[RenderDefinition],
      interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT,
      paint: Boolean = true // whether to paint the tile or return raw values
  ): PaintableTool = new PaintableTool {

    def tms(z: Int, x: Int, y: Int)(
        implicit cs: ContextShift[IO]): IO[Interpreted[MultibandTile]] = {
      val eval = LayerTms(IO.pure(expr), IO.pure(paramMap), interpreter)
      eval(z, x, y)
    }

    def extent(extent: Extent, cellsize: CellSize)(
        implicit cs: ContextShift[IO]): IO[Interpreted[MultibandTile]] = {
      val eval = LayerExtent(IO.pure(expr), IO.pure(paramMap), interpreter)
      eval(extent, cellsize)
    }

    def histogram(maxCellsSampled: Int)(implicit cs: ContextShift[IO])
      : IO[Interpreted[List[Histogram[Double]]]] = {

      logger.debug(s"Cells to sample: $maxCellsSampled")
      LayerHistogram(IO.pure(expr), IO.pure(paramMap), interpreter, 2000000)
    }

    def renderDefinition: Option[RenderDefinition] = renderDef
  }
}
