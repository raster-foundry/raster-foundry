package com.azavea.rf.tool.ast

import java.util.UUID

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import geotrellis.raster._
import geotrellis.raster.op._
import geotrellis.raster.testkit._
import geotrellis.raster.render._
import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest._
import scalaz._
import Scalaz._

class MapAlgebraOpSpec
    extends FunSpec
       with Matchers
       with TileBuilders
       with RasterMatchers {
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

  it("should evaluate classification") {
    requests = Nil
    val undefined = nir.copy(value = None)
    // This breakmap should convert all cells (which are set to a value of 5) to 77
    val breakmap = ClassBreaks(Map(6.0 -> 77), LessThanOrEqualTo, 123)
    val tms = Interpreter.tms(
      ast = red.classify(breakmap), source = goodSource
    )

    val ret: Future[Option[Op]] = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds)

    val maybeTile = op.flatMap(_.toTile(IntCellType))

    requests.length should be (1)
    assertEqual(maybeTile.get, createValueTile(4, 77).toArray)
  }
}
