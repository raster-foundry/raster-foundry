package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.color.{Implicits => ColorImplicits}
import com.rasterfoundry.datamodel._

import cats.effect._
import com.azavea.maml.ast.Expression
import com.azavea.maml.error.Interpreted
import com.azavea.maml.eval.ConcurrentInterpreter
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.{io => _, _}
import geotrellis.raster.histogram.Histogram
import geotrellis.server._
import geotrellis.vector.{io => _, _}
import io.chrisdavenport.log4cats.Logger

sealed trait PaintableTool extends ColorImplicits with LazyLogging {
  def tms(z: Int, x: Int, y: Int)(implicit
      cs: ContextShift[IO],
      logger: Logger[IO]
  ): IO[Interpreted[MultibandTile]]
  def extent(extent: Extent, cellsize: CellSize)(implicit
      cs: ContextShift[IO],
      logger: Logger[IO]
  ): IO[Interpreted[MultibandTile]]
  def histogram(maxCellsSampled: Int)(implicit
      cs: ContextShift[IO],
      logger: Logger[IO]
  ): IO[Interpreted[List[Histogram[Double]]]]

  def renderDefinition: Option[RenderDefinition]
}

object PaintableTool {
  def apply[
      Param: TmsReification[IO, ?]: ExtentReification[IO, ?]: HasRasterExtents[
        IO,
        ?
      ]
  ](
      expr: Expression,
      paramMap: Map[String, Param],
      renderDef: Option[RenderDefinition]
  ): PaintableTool =
    new PaintableTool {

      def tms(z: Int, x: Int, y: Int)(implicit
          cs: ContextShift[IO],
          logger: Logger[IO]
      ): IO[Interpreted[MultibandTile]] = {
        val interpreter = ConcurrentInterpreter.DEFAULT[IO]
        val eval = LayerTms(IO.pure(expr), IO.pure(paramMap), interpreter, None)
        eval(z, x, y)
      }

      def extent(extent: Extent, cellsize: CellSize)(implicit
          cs: ContextShift[IO],
          logger: Logger[IO]
      ): IO[Interpreted[MultibandTile]] = {
        val interpreter = ConcurrentInterpreter.DEFAULT[IO]
        val eval =
          LayerExtent(IO.pure(expr), IO.pure(paramMap), interpreter, None)
        eval(extent, Some(cellsize))
      }

      def histogram(maxCellsSampled: Int)(implicit
          cs: ContextShift[IO],
          logger: Logger[IO]
      ): IO[Interpreted[List[Histogram[Double]]]] = {
        val interpreter = ConcurrentInterpreter.DEFAULT[IO]
        logger.debug(s"Cells to sample: $maxCellsSampled")
        LayerHistogram(IO.pure(expr), IO.pure(paramMap), interpreter, 2000000)
      }

      def renderDefinition: Option[RenderDefinition] = renderDef
    }
}
