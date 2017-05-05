package com.azavea.rf.batch.ingest.spark

import com.azavea.rf.batch.ingest.model._

import geotrellis.raster._
import geotrellis.raster.testkit._

import org.scalatest.FunSpec

class NoDataPatternSpec extends FunSpec
    with TileBuilders
    with RasterMatchers {

  val checkeredTile = IntArrayTile(Array(255, 0, 0, 255), 2, 2)
  val allWhiteTile  = IntArrayTile(Array(0, 0, 0, 0), 2, 2)
  val allBlackTile  = IntArrayTile(Array(255, 255, 255, 255), 2, 2)


  describe("A NoDataPattern") {
    it("should correctly create a mask when all cases match the pattern") {
      val mbtile = MultibandTile(allWhiteTile, allWhiteTile, allWhiteTile, allBlackTile)
      val pattern = OutputDefinition.NoDataPattern(Map(0 -> 0, 1 -> 0, 2 -> 0, 3 -> 255))

      pattern.createMask(mbtile) shouldEqual createValueTile(2, NODATA)
    }

    it("should correctly create a mask when no cases match") {
      val mbtile = MultibandTile(allWhiteTile, allWhiteTile, allWhiteTile, allBlackTile)
      val pattern = OutputDefinition.NoDataPattern(Map(0 -> 0, 1 -> 0, 2 -> 255, 3 -> 0))

      pattern.createMask(mbtile) shouldEqual createValueTile(2, 1)
    }

    it("should correctly create a mask when one, but not all cases match") {
      val mbtile = MultibandTile(allWhiteTile, allWhiteTile, allWhiteTile, allBlackTile)
      val pattern = OutputDefinition.NoDataPattern(Map(0 -> 0, 1 -> 0, 2 -> 255, 3 -> 255))

      pattern.createMask(mbtile) shouldEqual createValueTile(2, 1)
    }

    it("should convert a multibandtile in which all cells match the pattern to a multibandtile filled with NODATA") {
      val mbtile = MultibandTile(allWhiteTile, allWhiteTile, allWhiteTile, allBlackTile)
      val pattern = OutputDefinition.NoDataPattern(Map(0 -> 0, 1 -> 0, 2 -> 0, 3 -> 255))

      pattern(mbtile).band(0) shouldEqual createValueTile(2, NODATA)
    }

    it("should handle a checkered pattern") {
      val mbtile = MultibandTile(checkeredTile, allWhiteTile, allWhiteTile, allBlackTile)
      val pattern = OutputDefinition.NoDataPattern(Map(0 -> 0, 1 -> 0, 2 -> 0, 3 -> 255))

      pattern(mbtile).band(0) shouldEqual IntArrayTile(Array(255, NODATA, NODATA, 255), 2, 2)
    }
  }
}


