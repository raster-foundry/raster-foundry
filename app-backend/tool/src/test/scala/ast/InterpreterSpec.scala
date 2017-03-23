package com.azavea.rf.tool.ast

import java.util.UUID

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import geotrellis.raster._
import geotrellis.raster.op._
import geotrellis.raster.testkit._
import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest._
import scalaz._
import Scalaz._

class InterpreterSpec extends FunSpec with Matchers with TileBuilders {
  import MapAlgebraAST._

  val scene = SceneRaster(UUID.randomUUID, None)

  val red = RFMLRasterSource(
    id = UUID.randomUUID, label = None,
    value = scene.copy(band = 4.some).some)

  val nir = RFMLRasterSource(
    id = UUID.randomUUID, label = None,
    value = scene.copy(band = 5.some).some)

  val tile: MultibandTile = ArrayMultibandTile(
    createValueTile(d = 4, v = 1),
    createValueTile(d = 4, v = 2),
    createValueTile(d = 4, v = 3),
    createValueTile(d = 4, v = 4),
    createValueTile(d = 4, v = 5),
    createValueTile(d = 4, v = 6)
  )

  var requests = List.empty[RFMLRaster]
  val goodSource = (raster: RFMLRaster, z: Int, x: Int, y: Int) => {
    raster match {
      case r@SceneRaster(id, Some(band)) =>
        requests = r :: requests
        Future.successful(tile.bands(band)some)
      case _ => Future.failed(new Exception("can't find that"))
    }
  }

  it("should evaluate simple ast") {
    val tms = Interpreter.tms(
      ast = red - nir, source = goodSource
    )

    val ret: Future[Option[Op]] = tms(0,1,1)
    val op = Await.result(ret, 10.seconds).get
    val tile = op.toTile(IntCellType).get

    requests.length should be (2)
    tile should be (createValueTile(4, -1))
  }


  it("should not fetch unless the AST is fully defined") {
    requests = Nil
    val undefined = nir.copy(value = None)
    val tms = Interpreter.tms(
      ast = red - undefined, source = goodSource
    )

    val ret: Future[Option[Op]] = tms(0,1,1)
    val op = Await.result(ret, 10.seconds)

    requests should be (empty)
    op should be (None)
  }

  it("should deal with bad raster sources") {

  }

  it("should deal with out of bounds band index") {

  }
}
