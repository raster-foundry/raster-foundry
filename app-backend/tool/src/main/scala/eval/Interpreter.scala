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
import geotrellis.raster._


/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
object Interpreter extends LazyLogging {

  /** The Interpreted type is either a list of failures or a compiled MapAlgebra operation */
  type Interpreted[A] = ValidatedNel[InterpreterError, A]

  @SuppressWarnings(Array("TraversableHead"))
  def interpretOperation(op: Operation, eval: MapAlgebraAST => LazyTile): LazyTile = op match {
    case Addition(args, id, _) =>
      logger.debug(s"case addition at $id")
      args.map(eval).reduce(_ + _)

    case Subtraction(args, id, _) =>
      logger.debug(s"case subtraction at $id")
      args.map(eval).reduce(_ - _)

    case Multiplication(args, id, _) =>
      logger.debug(s"case multiplication at $id")
      args.map(eval).reduce(_ * _)

    case Division(args, id, _) =>
      logger.debug(s"case division at $id")
      args.map(eval).reduce(_ / _)

    case Max(args, id, _) =>
      logger.debug(s"case max at $id")
      args.map(eval).reduce(_ max _)

    case Min(args, id, _) =>
      logger.debug(s"case min at $id")
      args.map(eval).reduce(_ min _)

    case Classification(args, id, _, breaks) =>
      logger.debug(s"case classification at $id with breakmap ${breaks.toBreakMap}")
      eval(args.head).classify(breaks.toBreakMap)
  }

  /** Interpret an AST with its matched execution parameters, but do so
    * without fetching any Rasters. Only interprets the structural validatity of
    * the AST, given the params.
    */
  def interpretPure[M: Monoid](
    ast: MapAlgebraAST,
    sourceMapping: Map[UUID, RFMLRaster]
  ): Interpreted[M] = ast match {
    /* Validate leaf nodes */
    case Source(id, _) if sourceMapping.isDefinedAt(id) => Valid(Monoid.empty)
    case Source(id, _) => Invalid(NonEmptyList.of(MissingParameter(id)))

    /* Unary operations must have only one arguments */
    case op: UnaryOp => {
      /* Check for errors further down, first */
      val kids: Interpreted[M] = op.args.foldMap(a => interpretPure(a, sourceMapping))

      if (op.args.length == 1) kids else {
        /* Add this error to any from lower down via their Semigroup instance */
        Invalid(NonEmptyList.of(IncorrectArgCount(op.id, 1, op.args.length))).combine(kids)
      }
    }

    /* All binary operations must have at least 2 arguments */
    case op => {
      val kids: Interpreted[M] = op.args.foldMap(a => interpretPure(a, sourceMapping))

      if (op.args.length > 1) kids else {
        Invalid(NonEmptyList.of(IncorrectArgCount(op.id, 2, op.args.length))).combine(kids)
      }
    }
  }

  /** The Interpreter method for producing a global, zoom-level 1 tile
    *
    * @param ast     A [[MapAlgebraAST]] which defines transformations over arbitrary rasters
    * @param source  A function from an [[RFMLRaster]] and z/x/y (tms) integers to possibly
    *                 existing tiles
    */
  def interpretGlobal(
    ast: MapAlgebraAST,
    sourceMapping: Map[UUID, RFMLRaster],
    source: RFMLRaster => Future[Option[Tile]]
  )(implicit ec: ExecutionContext): Future[Interpreted[LazyTile]] = {

    def eval(tiles: Map[UUID, Tile], ast: MapAlgebraAST): LazyTile = ast match {
      case Source(id, _) => LazyTile(tiles(id))
      case op: Operation => interpretOperation(op, { tree => eval(tiles, tree) })
      case unsupported =>
        throw new java.lang.IllegalStateException(s"Pattern match failure on putative AST: $unsupported")
    }

    val pure: Interpreted[Unit] = interpretPure[Unit](ast, sourceMapping)

    sourceMapping
      .mapValues(r => source(r).map(_.toRight(r.id)).recover({ case _ => Left(r.id) }))
      .sequence
      .map({ sms =>
        val tiles: Interpreted[Map[UUID, Tile]] = sms.map({
          case (id, Left(rid)) => (id, Invalid(NonEmptyList.of(RasterRetrievalError(id, rid))))
          case (id, Right(tile)) => (id, Valid(tile))
        }).sequence

        (pure |@| tiles).map({ case (_, ts) => eval(ts, ast) })
      })
  }

  /** The Interpreter method for producing z/x/y TMS tiles
    *
    * @param ast     A [[MapAlgebraAST]] which defines transformations over arbitrary rasters
    * @param source  A function from an [[RFMLRaster]] and z/x/y (tms) integers to possibly
    *                 existing tiles
    */
  def interpretTMS(
    ast: MapAlgebraAST,
    sourceMapping: Map[UUID, RFMLRaster],
    source: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]]
  )(implicit ec: ExecutionContext): (Int, Int, Int) => Future[Interpreted[LazyTile]] = {

    (z: Int, x: Int, y: Int) => {
      interpretGlobal(ast, sourceMapping, { raster: RFMLRaster => source(raster, z, x, y) })
    }
  }
}
