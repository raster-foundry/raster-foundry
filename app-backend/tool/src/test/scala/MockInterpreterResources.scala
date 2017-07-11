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


trait MockInterpreterResources extends TileBuilders with RasterMatchers {

  def randomSourceAST = MapAlgebraAST.Source(UUID.randomUUID, None)

  def tileSource(band: Int) = SceneRaster(UUID.randomUUID, Some(band), None)

  def tile(value: Int): Tile = createValueTile(d = 256, v = value)

  var requests = List.empty[RFMLRaster]

  val goodSource = (raster: RFMLRaster, buffer: Boolean, z: Int, x: Int, y: Int) => {
    raster match {
      case r@SceneRaster(id, Some(band), maybeND) =>
        requests = raster :: requests
        if (buffer)
          Future {
            Some(TileProvider(
              tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)),
              Some(Buffers(
                tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)),
                tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)),
                tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)),
                tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)),
                tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)),
                tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)),
                tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)),
                tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType))
              ))
            ))
          }
        else
          Future { Some(TileProvider(tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)), None)) }
      case _ => Future.failed(new Exception("can't find that"))
    }
  }
}
