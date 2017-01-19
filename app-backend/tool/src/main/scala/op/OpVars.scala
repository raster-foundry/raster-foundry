package geotrellis.raster.op

import geotrellis.raster._

/** Builder for variable assignment to Op tree.
  * This mostly exists to provide batch variable assignment capability
  */
//class OpVars(root: Op, vars: Map[Op.Var, Op]) {

//  def result: Op = {
//    println(vars)
//    root.bind(vars)
//  }

//  def set(variable: Op.Var, op: Op): OpVars =
//    new OpVars(root, vars.updated(variable, op))

//  def set(name: Symbol, op: Op): OpVars =
//    new OpVars(root, vars.updated(Op.Var(name), op))

//  def set(name: Symbol, mb: MultibandTile): OpVars = {
//    val bands =
//      for (i <- 0 until mb.bandCount)
//      yield Op.Var(name, i) -> Op(mb.bands(i))
//    println("the bands set as:", vars ++ bands)

//    new OpVars(root, vars ++ bands)
//  }
//}
