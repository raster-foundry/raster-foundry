package com.azavea.rf.batch

import java.util.UUID

import scala.reflect.ClassTag

import cats.implicits._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.eval.Interpreter
import com.azavea.rf.tool.eval.Interpreter.Interpreted
import geotrellis.raster.Tile
import geotrellis.spark._
import org.apache.spark.rdd.RDD

// --- //

package object ast {

  /** Evaluate an AST of RDD Sources. */
  def interpretRDD[K: ClassTag](
    ast: MapAlgebraAST,
    sourceMapping: Map[UUID, RFMLRaster],
    foo: RFMLRaster => RDD[(K, Tile)]
  ): Interpreted[RDD[(K, Tile)]] = {

    /* Perform Map Algebra over a validated RDD-filled AST */
    def eval(ast: MapAlgebraAST): RDD[(K, Tile)] = ast match {
      case Source(id, _) => foo(sourceMapping(id)) // TODO: Handle metadata replacement?
      case Addition(args, _, _) => args.map(eval).reduce(_ + _)
      case Subtraction(args, _, _) => args.map(eval).reduce(_ - _)
      case Multiplication(args, _, _) => args.map(eval).reduce(_ * _)
      case Division(args, _, _) => args.map(eval).reduce(_ / _)
      case _ => ??? // Beware...
    }

    /* Guarantee correctness before performing Map Algebra */
    Interpreter.interpretPure[Unit](ast, sourceMapping).map(_ => eval(ast))
  }
}
