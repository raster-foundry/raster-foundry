package com.azavea.rf.tool.eval

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

import cats._
import cats.data._
import cats.data.Validated._
import cats.implicits._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling._
import geotrellis.vector.{Extent, MultiPolygon}

/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
object PureInterpreter extends LazyLogging {

  /** Does a given AST have at least one source? */
  private def hasSources[M: Monoid](ast: MapAlgebraAST): Interpreted[M] = {
    if (ast.sources.exists({
      case Source(_, _) | LiteralRaster(_, _, _) | SceneRaster(_, _, _, _, _) | ProjectRaster(_, _, _, _, _) => true
      case _ => false
    })) Valid(Monoid.empty) else {
      Invalid(NonEmptyList.of(NoSourceLeaves(ast.id)))
    }
  }

  /** Interpret an AST with its matched execution parameters, but do so
    * without fetching any Rasters. Only interprets the structural validatity of
    * the AST, given the params.
    *
    * @param allowUnevaluable A boolean flag for determining whether `ToolReference` nodes are valid
    */
  def interpret[M: Monoid](
    ast: MapAlgebraAST,
    allowUnevaluable: Boolean
  ): Interpreted[M] = ast match {
    /* Validate leaf nodes */
    case Source(id, _) if !allowUnevaluable => Invalid(NonEmptyList.of(MissingParameter(id)))
    case Source(id, _) if allowUnevaluable => Valid(Monoid.empty)
    case ToolReference(id, _) if !allowUnevaluable => Invalid(NonEmptyList.of(UnsubstitutedRef(id)))
    case ToolReference(id, _) if allowUnevaluable => Valid(Monoid.empty)
    case SceneRaster(id, _,  _, _, _) => Valid(Monoid.empty)
    case ProjectRaster(id, _, _, _, _) => Valid(Monoid.empty)
    case Constant(_, _, _) => Valid(Monoid.empty)
    case LiteralRaster(_, _, _) => Valid(Monoid.empty)

    /* Unary operations must have only one arguments */
    case op: UnaryOperation => {
      /* Check for errors further down, first */
      val kids: Interpreted[M] = op.args.foldMap(a => interpret(a, allowUnevaluable))

      /* Unary ops must only have one argument */
      val argLen: Interpreted[M] = if (op.args.length == 1) Valid(Monoid.empty) else {
        Invalid(NonEmptyList.of(IncorrectArgCount(op.id, 1, op.args.length)))
      }

      /* Combine these (potential) errors via their Semigroup instance */
      kids.combine(argLen).combine(hasSources(op))
    }

    /* All binary operations must have at least 2 arguments */
    case op: Operation => {
      val kids: Interpreted[M] = op.args.foldMap(a => interpret(a, allowUnevaluable))

      val argLen: Interpreted[M] = if (op.args.length > 1) Valid(Monoid.empty) else {
        Invalid(NonEmptyList.of(IncorrectArgCount(op.id, 2, op.args.length)))
      }

      kids.combine(argLen)
    }
  }
}

