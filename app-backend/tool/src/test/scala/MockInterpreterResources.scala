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

  def tileRef(band: Int) = SceneRaster(UUID.randomUUID, Some(band), None)

  def tile(value: Int): Tile = createValueTile(d = 256, v = value)

  var requests = List.empty[RFMLRaster]

  val constantSource = (raster: RFMLRaster, buffer: Boolean, z: Int, x: Int, y: Int) => {
    raster match {
      case r@SceneRaster(id, Some(band), maybeND) =>
        requests = raster :: requests
        if (buffer)
          Future {
            Some(TileWithNeighbors(
              tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)),
              Some(NeighboringTiles(
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
          Future { Some(TileWithNeighbors(tile(band).interpretAs(maybeND.getOrElse(tile(band).cellType)), None)) }
      case _ => Future.failed(new Exception("can't find that"))
    }
  }

  val ascendingSource = (raster: RFMLRaster, buffer: Boolean, z: Int, x: Int, y: Int) => {
    val ascending = IntArrayTile(1 to 256*256 toArray, 256, 256)
    raster match {
      case r@SceneRaster(id, _, maybeND) =>
        requests = raster :: requests
        if (buffer)
          Future {
            Some(TileWithNeighbors(
              ascending.interpretAs(maybeND.getOrElse(ascending.cellType)),
              Some(NeighboringTiles(
                ascending.interpretAs(maybeND.getOrElse(ascending.cellType)),
                ascending.interpretAs(maybeND.getOrElse(ascending.cellType)),
                ascending.interpretAs(maybeND.getOrElse(ascending.cellType)),
                ascending.interpretAs(maybeND.getOrElse(ascending.cellType)),
                ascending.interpretAs(maybeND.getOrElse(ascending.cellType)),
                ascending.interpretAs(maybeND.getOrElse(ascending.cellType)),
                ascending.interpretAs(maybeND.getOrElse(ascending.cellType)),
                ascending.interpretAs(maybeND.getOrElse(ascending.cellType))
              ))
            ))
          }
        else
          Future { Some(TileWithNeighbors(ascending.interpretAs(maybeND.getOrElse(ascending.cellType)), None)) }
      case _ => Future.failed(new Exception("can't find that"))
    }
  }

  val moduloSource = (raster: RFMLRaster, buffer: Boolean, z: Int, x: Int, y: Int) => {
    def mod(modWhat: Int) = IntArrayTile((1 to 256*256).map(_ % modWhat) toArray, 256, 256)
    raster match {
      case r@SceneRaster(id, Some(band), maybeND) =>
        requests = raster :: requests
        if (buffer)
          Future {
            Some(TileWithNeighbors(
              mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType)),
              Some(NeighboringTiles(
                mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType)),
                mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType)),
                mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType)),
                mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType)),
                mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType)),
                mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType)),
                mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType)),
                mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType))
              ))
            ))
          }
        else
          Future { Some(TileWithNeighbors(mod(band).interpretAs(maybeND.getOrElse(mod(band).cellType)), None)) }
      case _ => Future.failed(new Exception("can't find that"))
    }
  }
}
