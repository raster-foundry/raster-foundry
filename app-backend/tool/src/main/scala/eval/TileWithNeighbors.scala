package com.azavea.rf.tool.eval

import geotrellis.raster._

import java.lang.IllegalStateException


case class NeighboringTiles(
  tl: Tile,
  tm: Tile,
  tr: Tile,
  ml: Tile,
  mr: Tile,
  bl: Tile,
  bm: Tile,
  br: Tile
)

case class TileWithNeighbors(centerTile: Tile, buffers: Option[NeighboringTiles]) {
  def withBuffer(buffer: Int): Tile = buffers match {
    case Some(buf) =>
      if (buffer > 0) {
        CompositeTile(
          Seq(
            buf.tl, buf.tm, buf.tr,
            buf.ml, centerTile, buf.mr,
            buf.bl, buf.bm, buf.br
          ),
          TileLayout(3, 3, centerTile.cols, centerTile.rows)
        ).crop(
          centerTile.cols - buffer,
          centerTile.rows - buffer,
          centerTile.cols * 2 + buffer - 1,
          centerTile.rows * 2 + buffer - 1
        )
      }
      else
        centerTile
    case None if (buffer == 0) =>
      centerTile
    case _ =>
      throw new IllegalStateException("Buffers being asked for despite not having been prefetched")
  }
}

