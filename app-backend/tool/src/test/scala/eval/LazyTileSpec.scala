package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._
import com.azavea.rf.tool.ast.codec.MapAlgebraCodec._
import com.azavea.rf.tool.ast.MapAlgebraAST._

import io.circe._
import io.circe.syntax._
import geotrellis.raster._
import geotrellis.raster.testkit._
import geotrellis.raster.render._
import org.scalatest._
import cats.data._
import cats.data.Validated._

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID


class LazyTileSpec
    extends FunSpec
       with Matchers
       with TileBuilders
       with RasterMatchers {

  def randomSourceAST = MapAlgebraAST.Source(UUID.randomUUID, None)

  val redTileSource = SceneRaster(UUID.randomUUID, Some(4))

  val nirTileSource = SceneRaster(UUID.randomUUID, Some(5))

  val blueTileSource = SceneRaster(UUID.randomUUID, Some(1))

  val tile: MultibandTile = ArrayMultibandTile(
    createValueTile(d = 4, v = 0),
    createValueTile(d = 4, v = 1),
    createValueTile(d = 4, v = 2),
    createValueTile(d = 4, v = 3),
    createValueTile(d = 4, v = 4),
    createValueTile(d = 4, v = 5)
  )

  var requests = List.empty[RFMLRaster]
  val goodSource = (raster: RFMLRaster, z: Int, x: Int, y: Int) => {
    raster match {
      case r@SceneRaster(id, Some(band)) =>
        requests = raster :: requests
        Future { Some(tile.bands(band)) }
      case _ => Future.failed(new Exception("can't find that"))
    }
  }

  it("should evaluate classification") {
    requests = Nil
    // This breakmap should convert all cells (which are set to a value of 5) to 77
    val breakmap = ClassBreaks(Map(6.0 -> 77), ClassBreaks.Options(LessThanOrEqualTo, 123))
    val srcAST = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = srcAST.classify(breakmap),
      params = EvalParams(Map(srcAST.id -> redTileSource)),
      source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazyTile) =>
        val maybeTile = lazyTile.evaluate
        requests.length should be (1)
        assertEqual(maybeTile.get, createValueTile(4, 77).toArray)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate subtraction") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 - src2,
      params = EvalParams(Map(src1.id -> blueTileSource, src2.id -> nirTileSource)),
      source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.get(0, 0) should be (1 - 5)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should preserve order of operations while evaluating subtraction") {
    requests = Nil
    // 4, 5, 1
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val src3 = randomSourceAST
    val ast = Subtraction(List(src1, src2, src3), UUID.randomUUID(), None)
    val tms = Interpreter.interpretTMS(
      ast = ast,
      params = EvalParams(Map(src1.id -> nirTileSource, src2.id -> redTileSource, src3.id -> blueTileSource)),
      source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (ast.args.length)
        maybeTile.get.get(0, 0) should be (5 - 4 - 1)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate division") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 / src2,
      params = EvalParams(Map(src1.id -> redTileSource, src2.id -> nirTileSource)),
      source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (4.0 / 5.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should preserve order of operations while evaluating division") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val src3 = randomSourceAST
    val ast = Division(List(src1, src2, src3), UUID.randomUUID(), None)
    val tms = Interpreter.interpretTMS(
      ast = ast,
      params = EvalParams(Map(src1.id -> blueTileSource, src2.id -> nirTileSource, src3.id -> redTileSource)),
      source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (ast.args.length)
        maybeTile.get.getDouble(0, 0) should be (1.0 / 5.0 / 4.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate multiplication") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 * src2,
      params = EvalParams(Map(src1.id -> redTileSource, src2.id -> nirTileSource)),
      source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (4.0 * 5.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate addition") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 + src2,
      params = EvalParams(Map(src1.id -> redTileSource, src2.id -> nirTileSource)),
      source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val maybeTile = lazytile.evaluateDouble
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (4.0 + 5.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }
}
