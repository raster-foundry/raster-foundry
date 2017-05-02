package com.azavea.rf.tool.eval

import java.util.UUID

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import cats.data.{NonEmptyList => NEL, _}
import cats.data.Validated._
import cats.implicits._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._
import geotrellis.raster._
import geotrellis.raster.testkit._
import org.scalatest._


class InterpreterSpec
    extends FunSpec
       with Matchers
       with TileBuilders
       with RasterMatchers {

  def randomSourceAST = MapAlgebraAST.Source(UUID.randomUUID, None)

  val redTileSource = SceneRaster(UUID.randomUUID, Some(4))

  val nirTileSource = SceneRaster(UUID.randomUUID, Some(5))

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
        requests = r :: requests
        Future.successful { Some(tile.bands(band)) }
      case _ =>
        Future.failed(new Exception("can't find that"))
    }
  }

  it("should evaluate simple ast") {
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 - src2,
      sourceMapping = Map(src1.id -> redTileSource, src2.id -> nirTileSource),
      source = goodSource
    )

    val ret = tms(0,1,1)
    val tile = Await.result(ret, 10.seconds) match {
      case Valid(lazytile) =>
        val tile = lazytile.evaluate
        requests.length should be (2)
        assertEqual(tile.get, createValueTile(4, -1).toArray)
      case i@Invalid(_) =>
        fail(s"Unable to parse tree for tile retrieval: $i")
    }
  }


  it("should not fetch unless eval paramaters are fully capable of filling out the AST") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val tms = Interpreter.interpretTMS(
      ast = src1 - src2,
      sourceMapping = Map(src1.id -> redTileSource),
      source = goodSource
    )

    val ret = tms(0,1,1)
    val lt = Await.result(ret, 10.seconds)

    requests.length should be (1)
    lt should be (Invalid(NEL.of(MissingParameter(src2.id))))
  }

  /** A failed future is semantically different than successfully returned None. This test
    *  allows us to simulate a failure encountered while retrieving a tile as opposed to
    *  an empty region of some tile layout.
    */
  it("should deal with bad raster sourceMapping and aggregate multiple errors") {
    requests = Nil
    val src1 = randomSourceAST
    val src2 = randomSourceAST
    val badSource = (raster: RFMLRaster, z: Int, x: Int, y: Int) => Future.failed { new Exception("Some exception") }

    val tms = Interpreter.interpretTMS(
      ast = src1 - src2,
      sourceMapping = Map(src1.id -> redTileSource),
      source = badSource
    )

    val ret = tms(0,1,1)
    val lt = Await.result(ret, 10.seconds)

    requests should be (empty)
    lt should be (Invalid(NEL.of(RasterRetrievalError(src1.id, redTileSource.id), MissingParameter(src2.id))))
  }

  it("interpretPure") {
    val src1 = randomSourceAST
    val src2 = randomSourceAST

    val tms = Interpreter.interpretPure[Unit](
      ast = src1 - src2,
      sourceMapping = Map(src1.id -> redTileSource, src2.id -> nirTileSource)
    )

    tms shouldBe Valid(())

  }

}
