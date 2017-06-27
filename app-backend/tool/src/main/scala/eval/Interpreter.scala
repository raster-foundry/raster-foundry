package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.params._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.params.ParamOverride

import cats._
import cats.data._
import cats.data.Validated._
import cats.implicits._

import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

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

  def overrideParams(
    ast: MapAlgebraAST,
    overrides: Map[UUID, ParamOverride]
  ): Interpreted[MapAlgebraAST] = ast match {
    /* Can't override data Sources */
    case s: Source => Valid(s)

    /* Nodes which can be overridden */
    case c: Constant => overrides.get(c.id) match {
      case None => Valid(c)
      case Some(ParamOverride.Constant(const)) => Valid(c.copy(constant = const))
      case Some(_) => Invalid(NonEmptyList.of(InvalidOverride(c.id)))
    }

    case Classification(args, id, m, b) => {
      val kids: Interpreted[List[MapAlgebraAST]] =
        args.map(a => overrideParams(a, overrides)).sequence

      val breaks: Interpreted[ClassMap] = overrides.get(id) match {
        case None => Valid(b)
        case Some(ParamOverride.Classification(bs)) => Valid(bs)
        case Some(_) => Invalid(NonEmptyList.of(InvalidOverride(id)))
      }

      (kids |@| breaks).map({ case (ks, bs) => Classification(ks, id, m, bs) })
    }

    /* Non-overridable Operations */
    case Addition(args, id, m) =>
      args.map(a => overrideParams(a, overrides)).sequence.map(ks => Addition(ks, id, m))
    case Subtraction(args, id, m) =>
      args.map(a => overrideParams(a, overrides)).sequence.map(ks => Subtraction(ks, id, m))
    case Multiplication(args, id, m) =>
      args.map(a => overrideParams(a, overrides)).sequence.map(ks => Multiplication(ks, id, m))
    case Division(args, id, m) =>
      args.map(a => overrideParams(a, overrides)).sequence.map(ks => Division(ks, id, m))
    case Min(args, id, m) =>
      args.map(a => overrideParams(a, overrides)).sequence.map(ks => Min(ks, id, m))
    case Max(args, id, m) =>
      args.map(a => overrideParams(a, overrides)).sequence.map(ks => Max(ks, id, m))
    case op => Invalid(NonEmptyList.of(UnhandledCase(op.id)))
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
    case Constant(_, _, _) => Valid(Monoid.empty)

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
    overrides: Map[UUID, ParamOverride],
    source: RFMLRaster => Future[Option[Tile]]
  )(implicit ec: ExecutionContext): Future[Interpreted[LazyTile]] = {

    def eval(tiles: Map[UUID, Tile], ast: MapAlgebraAST): LazyTile = ast match {
      case Source(id, _) => LazyTile(tiles(id))
      case Constant(_, const, _) => LazyTile.Constant(const)
      case op: Operation => interpretOperation(op, { tree => eval(tiles, tree) })
      case unsupported =>
        throw new java.lang.IllegalStateException(s"Pattern match failure on putative AST: $unsupported")
    }

    val pure: Interpreted[Unit] = interpretPure[Unit](ast, sourceMapping)
    val overridden: Interpreted[MapAlgebraAST] = overrideParams(ast, overrides)

    sourceMapping
      .mapValues(r => source(r).map(_.toRight(r.id)).recover({ case t: Throwable => Left(r.id) }))
      .sequence
      .map({ sms =>
        val tiles: Interpreted[Map[UUID, Tile]] = sms.map({
          case (id, Left(rid)) => (id, Invalid(NonEmptyList.of(RasterRetrievalError(id, rid))))
          case (id, Right(tile)) => (id, Valid(tile))
        }).sequence

        (pure |@| overridden |@| tiles).map({ case (_, tree, ts) => eval(ts, tree) })
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
    overrides: Map[UUID, ParamOverride],
    source: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]]
  )(implicit ec: ExecutionContext): (Int, Int, Int) => Future[Interpreted[LazyTile]] = {

    (z: Int, x: Int, y: Int) => {
      interpretGlobal(ast, sourceMapping, overrides, { raster: RFMLRaster => source(raster, z, x, y) })
    }
  }
}
