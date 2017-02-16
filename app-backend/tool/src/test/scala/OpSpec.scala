package com.azavea.rf.tool

import geotrellis.raster._
import geotrellis.raster.op._

import org.scalatest._

class OpSpec extends FunSpec with Matchers {

  it("represents simple addition") {
    val op = Op('nir) + Op('red)
  }

  it("parses singleband identifiers") {
    val v1 = Op("red")
    v1 should be equals (Op.Var('red))
  }

  it("parses multiband index indentifiers") {
    val v1 = Op("red[2]")
    v1 should be equals (Op.Var('red, 2))
  }

  it("represents NDVI") {
    // this will be parsed from the "include" field
    val nir = Op("nir")
    val red = Op("red")
    val op = (red - nir) / (red + nir)
  }

  it("accepts MultibandTile parameters") {
    val nir = Op("LC8[0]")
    val red = Op("LC8[1]")
    val op = (red - nir) / (red + nir)
  }

  it("thing we are doing"){
    val nir = Op("nir")
    val red = Op("red")

    // this is a tree, it has 2x"nir" and 2x"red" leaves
    val ndvi: Op = (red - nir) / (red + nir)

    val LC8Tile: MultibandTile = MultibandTile(
      DoubleArrayTile.fill(1,2,2),
      DoubleArrayTile.fill(2,2,2),
      DoubleArrayTile.fill(3,2,2)
    )

    val mbNDVI1 = ndvi.vars
      .set('nir, Op("LC8[0]"))
      .set('red, Op("LC8[1]"))
      .result

    val mbNDVI2 = ndvi.vars
      .set('nir, Op("LC8[1]"))
      .set('red, Op("LC8[2]"))
      .result

    val result = mbNDVI1

    val answer = result.vars.set('LC8, LC8Tile).result

    val tile = answer.toTile(FloatCellType)
  }

  // THOUGHTS:
  // what I am finding myself thinking it is pointless to have Var that fits Op interface.
  // - What makes more sense is to have a structure of unbound functions that can be bound
  // - How does this structure become partially bound ?
  // - can we ignore it for now ? Yes
}
