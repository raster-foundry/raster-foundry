package com.azavea.rf.ingest

import com.azavea.rf.ingest.tool._

import java.util.UUID

import org.scalatest.{Matchers, FunSpec}

import geotrellis.raster.testkit._
import geotrellis.raster._

class NoDataPatternSpec extends FunSpec
    with TileBuilders
    with RasterMatchers {

  val allBlack = createValueTile(255, 255)
  val allWhite = createValueTile(255, 0)

  describe("A NoDataPattern") {
    it("should correctly create a mask when all cases match the pattern") {
      val mbtile = MultibandTile(allWhite, allWhite, allWhite, allBlack)
      val pattern = OutputDefinition.NoDataPattern(Map(0 -> 0, 1 -> 0, 2 -> 0, 3 -> 255))
      pattern.createMask(mbtile) shouldEqual createValueTile(255, 1)
    }

    it("should correctly create a mask when no cases match") {
      val mbtile = MultibandTile(allWhite, allWhite, allWhite, allBlack)
      val pattern = OutputDefinition.NoDataPattern(Map(0 -> 0, 1 -> 0, 2 -> 255, 3 -> 0))
      pattern.createMask(mbtile) shouldEqual createValueTile(255, 0)
    }

    it("should correctly create a mask when one, but not all cases match") {
      val mbtile = MultibandTile(allWhite, allWhite, allWhite, allBlack)
      val pattern = OutputDefinition.NoDataPattern(Map(0 -> 0, 1 -> 0, 2 -> 255, 3 -> 255))
      pattern.createMask(mbtile) shouldEqual createValueTile(255, 0)
    }
  }
}


