package geotrellis.raster.op

import geotrellis.raster._
import geotrellis.raster.render.BreakMap

case class Reclassify(left: Op, breakMap: BreakMap[Double, Double]) extends Op.Tree {
  // TODO: Consider how to represent mappings from Double=>Int and Int=>Int
  def get(col: Int, row: Int): Int = d2i(breakMap(left.getDouble(col, row)))
  def getDouble(col: Int, row: Int): Double = breakMap(left.getDouble(col, row))
  def bind(args: Map[Op.Var, Op]): Op = Reclassify(left.bind(args), breakMap)
  def right = Op.Nil
}
