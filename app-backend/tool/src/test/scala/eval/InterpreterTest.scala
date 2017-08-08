package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._
import com.azavea.rf.tool.ast.MapAlgebraAST._

import io.circe._
import io.circe.syntax._
import cats.data.{NonEmptyList => NEL, _}
import cats.data.Validated._
import cats.implicits._
import org.scalatest._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.testkit._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import java.util.UUID


object InterpreterTest
    extends FunSpec
       with Matchers {

  def int(
    ast: MapAlgebraAST,
    srcMap: Map[UUID, RFMLRaster],
    tileSource: (RFMLRaster, Boolean, Int, Int, Int) => Future[Option[TileWithNeighbors]],
    overrides: Map[UUID, ParamOverride],
    label: String
  ): Tile = {
    val tms = Interpreter.interpretTMS(
      ast = ast,
      sourceMapping = srcMap,
      overrides = overrides,
      tileSource = tileSource,
      256
    )
    if (label.length > 0) println(s"$label: ", ast.asJson.noSpaces)

    val ret = tms(1, 1, 1)
    Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val tile = lazytile.evaluate.get
        tile
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  def dbl(
    ast: MapAlgebraAST,
    srcMap: Map[UUID, RFMLRaster],
    tileSource: (RFMLRaster, Boolean, Int, Int, Int) => Future[Option[TileWithNeighbors]],
    overrides: Map[UUID, ParamOverride],
    label: String
  ): Tile = {
    val tms = Interpreter.interpretTMS(
      ast = ast,
      sourceMapping = srcMap,
      overrides = overrides,
      tileSource = tileSource,
      256
    )
    if (label.length > 0) println(s"$label: ", ast.asJson.noSpaces)

    val ret = tms(1, 1, 1)
    Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val tile = lazytile.evaluateDouble.get
        tile
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }
}
