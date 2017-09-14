package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
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
    tileSource: (RFMLRaster, Boolean, Int, Int, Int) => Future[Interpreted[TileWithNeighbors]],
    label: String
  ): Tile = {
    val futureTile = BufferingInterpreter.literalize(ast, tileSource, 1, 1, 1).map({ validatedAst =>
      validatedAst
        .andThen(BufferingInterpreter.interpret(_, 256)(global)(1, 1, 1))
        .map(_.evaluate.get)
    })
    if (label.length > 0) println(s"$label: ", ast.asJson.noSpaces)

    Await.result(futureTile, 10.seconds) match {
      case Valid(tile) =>
        tile
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  def dbl(
    ast: MapAlgebraAST,
    tileSource: (RFMLRaster, Boolean, Int, Int, Int) => Future[Interpreted[TileWithNeighbors]],
    label: String
  ): Tile = {
    val futureTile = BufferingInterpreter.literalize(ast, tileSource, 1, 1, 1).map({ validatedAst =>
      validatedAst
        .andThen(BufferingInterpreter.interpret(_, 256)(global)(1, 1, 1))
        .map(_.evaluateDouble.get)
    })
    if (label.length > 0) println(s"$label: ", ast.asJson.noSpaces)

    Await.result(futureTile, 10.seconds) match {
      case Valid(tile) =>
        tile
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }
}
