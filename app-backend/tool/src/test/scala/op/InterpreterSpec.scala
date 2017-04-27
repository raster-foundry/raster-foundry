package com.azavea.rf.tool.op

import java.util.UUID

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import geotrellis.raster._
import geotrellis.raster.op._
import geotrellis.raster.testkit._
import geotrellis.raster.render._
import org.scalatest._
import cats.data.{NonEmptyList => NEL, _}
import cats.data.Validated._

import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.URI


class InterpreterSpec
    extends FunSpec
       with Matchers
       with TileBuilders
       with RasterMatchers {
  import MapAlgebraAST._

  val red = RFMLRasterSource(
    id = UUID.randomUUID,
    label = Some("RED"),
    value = Some(SceneRaster(UUID.randomUUID, Some(4)))
  )

  val nir = RFMLRasterSource(
    id = UUID.randomUUID,
    label = Some("NIR"),
    value = Some(SceneRaster(UUID.randomUUID, Some(5)))
  )

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

  val badSource = (raster: RFMLRaster, z: Int, x: Int, y: Int) => Future.successful { None }

  it("should evaluate simple ast") {
    val tms = Interpreter.tms(
      ast = red - nir, source = goodSource
    )

    val ret = tms(0,1,1)
    val tile = Await.result(ret, 10.seconds) match {
      case Valid(op) =>
        val tile = op.toTile(IntCellType)
        requests.length should be (2)
        assertEqual(tile.get, createValueTile(4, -1).toArray)
      case i@Invalid(_) =>
        fail(s"Unable to parse tree for tile retrieval: $i")
    }
  }


  it("should not fetch unless the AST is fully defined") {
    requests = Nil
    val undefined = nir.copy(value = None)
    val tms = Interpreter.tms(
      ast = red - undefined, source = goodSource
    )

    val ret = tms(0,1,1)
    val op = Await.result(ret, 10.seconds)

    requests.length should be (1)
    op should be (Invalid(NEL.of(MissingParameter(undefined.id))))
  }

  it("should deal with bad raster sources and aggregate multiple errors") {
    requests = Nil
    val undefined = nir.copy(value = None)
    val tms = Interpreter.tms(
      ast = red - undefined, source = badSource
    )

    val ret = tms(0,1,1)
    val op = Await.result(ret, 10.seconds)

    requests should be (empty)
    op should be (Invalid(NEL.of(RasterRetrievalError(red.id, red.value.get.id), MissingParameter(undefined.id))))
  }

  it("should deal with out of bounds band index") {
    requests = Nil
    val outOfBounds = RFMLRasterSource(
      id = UUID.randomUUID,
      label = Some("out of bounds"),
      value = Some(SceneRaster(UUID.randomUUID, Some(8)))
    )
    val tms = Interpreter.tms(
      ast = outOfBounds, source = badSource
    )

    val ret = tms(0,1,1)
    val op = Await.result(ret, 10.seconds)

    requests should be (empty)
    op should be (Invalid(NEL.of(RasterRetrievalError(outOfBounds.id, outOfBounds.value.get.id))))
  }
}

