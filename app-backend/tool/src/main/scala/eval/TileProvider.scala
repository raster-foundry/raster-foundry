package com.azavea.rf.tool.eval

import geotrellis.raster._

import java.security.InvalidParameterException


case class Buffers(
  tl: Tile,
  tm: Tile,
  tr: Tile,
  ml: Tile,
  mr: Tile,
  bl: Tile,
  bm: Tile,
  br: Tile
)

case class TileProvider(centerTile: Tile, buffers: Option[Buffers], options: TileProvider.Options = TileProvider.Options.DEFAULT) {
  def withBuffer(buffer: Int): Tile = buffers match {
    case Some(buf) =>
      if (buffer > 0) {
        val composite = CompositeTile(
          Seq(
            buf.tl, buf.tm, buf.tr,
            buf.ml, centerTile, buf.mr,
            buf.bl, buf.bm, buf.br
          ),
          TileLayout(3, 3, options.tileCols, options.tileRows)
        ).crop(
          options.tileCols - buffer,
          options.tileRows - buffer,
          options.tileCols * 2 + buffer,
          options.tileRows * 2 + buffer
        )
        println("composite...", buffer, composite.cols, composite.rows)
        composite
      }
      else
        centerTile
    case None if (buffer == 0) =>
      centerTile
    case _ =>
      throw new InvalidParameterException("Buffers being asked for despite not having been prefetched")
  }
}

object TileProvider {
  case class Options(tileCols: Int, tileRows: Int)
  object Options {
    val DEFAULT = Options(256, 256)
  }
}
