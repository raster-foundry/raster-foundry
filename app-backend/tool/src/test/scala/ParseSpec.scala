package com.azavea.rf.tool

import geotrellis.raster._
import geotrellis.raster.op._

import org.scalatest._

trait Tool {
  def eval(vars: (Symbol, MultibandTile)*): MultibandTile
}

class ParseSpec extends FunSpec with Matchers {
  it("parses apply of +") {
    val json = """ {"apply": "+", "args": ["red", "nir"]} """
    // val tool = OpParser.parse(json)
    val nirTile: Tile = ???
    val redTile: Tile = ???

    OpParser.parse(json)
  }

  it("it works on multiband tile") {
  //   val json =""" {"apply": "+", "args": ["LC8[0]", "LC8[1]"]} """
  //   // val tool = Parser.parse(json)
  //   val mbTile: MultibandTile = ???

  //   val resTile = tool.eval('nir -> nirTile, 'red -> redTile)
  //   resTile should be equal(redTile + nirTile)
  }

  it("works on multiband tiles") {

  }
}
