package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.maml._

import com.azavea.maml.eval._
import cats._
import cats.data._
import cats.data.Validated._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling._
import geotrellis.vector.{Extent, MultiPolygon}

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
object PureInterpreter extends LazyLogging {

  /** Does a given AST have at least one source? */
  private def hasSources[M: Monoid](ast: MapAlgebraAST): Interpreted[M] = {
    if (ast.sources.exists({
          case MapAlgebraAST.Source(_, _) | MapAlgebraAST.LiteralTile(_, _, _) |
              MapAlgebraAST.SceneRaster(_, _, _, _, _) |
              MapAlgebraAST.ProjectRaster(_, _, _, _, _) =>
            true
          case _ => false
        })) {
      Valid(Monoid[M].empty)
    } else {
      Invalid(
        NonEmptyList.of(
          NonEvaluableNode(ast.asMaml._1, Some("missing source nodes"))))
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
    case MapAlgebraAST.Source(id, _) if !allowUnevaluable =>
      Invalid(
        NonEmptyList.of(
          NonEvaluableNode(ast.asMaml._1, Some("missing source nodes"))))
    case MapAlgebraAST.Source(id, _) if allowUnevaluable =>
      Valid(Monoid[M].empty)
    case MapAlgebraAST.ToolReference(id, _) if !allowUnevaluable =>
      Invalid(
        NonEmptyList.of(
          NonEvaluableNode(ast.asMaml._1, Some("missing source nodes"))))
    case MapAlgebraAST.ToolReference(id, _) if allowUnevaluable =>
      Valid(Monoid[M].empty)
    case MapAlgebraAST.SceneRaster(id, _, _, _, _)   => Valid(Monoid[M].empty)
    case MapAlgebraAST.ProjectRaster(id, _, _, _, _) => Valid(Monoid[M].empty)
    case MapAlgebraAST.Constant(_, _, _)             => Valid(Monoid[M].empty)
    case MapAlgebraAST.LiteralTile(_, _, _)          => Valid(Monoid[M].empty)

    /* Unary operations must have only one arguments */
    case op: MapAlgebraAST.UnaryOperation => {
      /* Check for errors further down, first */
      val kids: Interpreted[M] =
        op.args.foldMap(a => interpret(a, allowUnevaluable))

      /* Unary ops must only have one argument */
      val argLen: Interpreted[M] =
        if (op.args.length == 1) Valid(Monoid[M].empty)
        else {
          Invalid(NonEmptyList.of(IncorrectArgCount(ast.asMaml._1, 1)))
        }

      /* Combine these (potential) errors via their Semigroup instance */
      kids.combine(argLen).combine(hasSources[M](op))
    }

    /* All binary operations must have at least 2 arguments */
    case op: MapAlgebraAST.Operation => {
      val kids: Interpreted[M] =
        op.args.foldMap(a => interpret(a, allowUnevaluable))

      val argLen: Interpreted[M] =
        if (op.args.length > 1) Valid(Monoid[M].empty)
        else {
          Invalid(NonEmptyList.of(IncorrectArgCount(ast.asMaml._1, 2)))
        }

      kids.combine(argLen)
    }
  }
}
