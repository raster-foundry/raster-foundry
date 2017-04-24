package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.params._
import com.azavea.rf.tool.ast.MapAlgebraAST._

import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.render.{BreakMap, MapStrategy}
import cats.data._
import cats.data.Validated._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import java.lang.IllegalStateException
import java.net.URI
import java.util.UUID


/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
object Interpreter extends LazyLogging {

  /** The Interpreted type is either a list of failures or a compiled MapAlgebra operation */
  type Interpreted = ValidatedNel[InterpreterError, LazyTile]

  // Binary operation evaluation
  def evalBinary(
    futureTiles: Seq[Future[Interpreted]],
    f: (LazyTile, LazyTile) => LazyTile
  )(implicit ec: ExecutionContext): Future[Interpreted] = {
    logger.debug("evalBinary")
    def applyB(accumulator: Interpreted, value: Interpreted): Interpreted = {
      (accumulator, value) match {
        case (Valid(acc), Valid(v)) =>
          Valid(f(acc, v))
        case (Invalid(e1), Invalid(e2)) =>
          Invalid(e1 concat e2)
        case (_, errors@Invalid(_)) =>
          errors
        case (errors@Invalid(_), _) =>
          errors
      }
    }

    Future.sequence(futureTiles).map { tiles =>
      tiles.tail.foldLeft(tiles.head)(applyB)
    }
  }

  // Unary operation evaluation
  def evalUnary(
    futureTile: Future[Interpreted],
    f: LazyTile => LazyTile
  )(implicit ec: ExecutionContext): Future[Interpreted] = {
    logger.debug("evalUnary")
    for (interpreted <- futureTile) yield {
      interpreted match {
        case Valid(lazyTile) =>
          Valid(f(lazyTile))
        case errors@Invalid(_) =>
          logger.debug(s"unary failure on $interpreted")
          errors
      }
    }
  }

  def interpretOperation(
    op: MapAlgebraAST.Operation,
    eval: MapAlgebraAST => Future[Interpreted]
  )(implicit ec: ExecutionContext) = op match {
    case Addition(args, id, _) =>
      logger.debug(s"case addition at $id")
      evalBinary(args.map(eval),  _ + _)

    case Subtraction(args, id, _) =>
      logger.debug(s"case subtraction at $id")
      evalBinary(args.map(eval),  _ - _)

    case Multiplication(args, id, _) =>
      logger.debug(s"case multiplication at $id")
      evalBinary(args.map(eval),  _ * _)

    case Division(args, id, _) =>
      logger.debug(s"case division at $id")
      evalBinary(args.map(eval),  _ / _)

    case Classification(args, id, _, breaks) =>
      logger.debug(s"case classification at $id with breakmap ${breaks.toBreakMap}")
      val breakmap = breaks.toBreakMap
      evalUnary(eval(args.head), _.classify(breaks.toBreakMap))
  }

  /** The Interpreter method for producing a global, zoom-level 1 tile
    *
    * @param ast     A [[MapAlgebraAST]] which defines transformations over arbitrary rasters
    * @param source  A function from an [[RFMLRaster]] and z/x/y (tms) integers to possibly
    *                 existing tiles
    */
  def interpretGlobal(
    ast: MapAlgebraAST,
    params: EvalParams,
    source: RFMLRaster => Future[Option[Tile]]
  )(implicit ec: ExecutionContext): Future[Interpreted] = {

    def eval(ast: MapAlgebraAST): Future[Interpreted] = ast match {
      case Source(id, label) =>
        if (params.sources.isDefinedAt(id)) {
          val rfmlRaster = params.sources(id)
          source(rfmlRaster) map { maybeTile =>
            val maybeLazyTile = maybeTile.map { tile => LazyTile(tile) }
            Validated.fromOption(maybeLazyTile, { NonEmptyList.of(RasterRetrievalError(id, rfmlRaster.id)) })
          }
        } else {
          Future.successful { Invalid(NonEmptyList.of(MissingParameter(id))) }
        }

      // For the exhaustive match
      case op: Operation =>
        interpretOperation(op, eval)
    }

    eval(ast)
  }

  /** The Interpreter method for producing z/x/y TMS tiles
    *
    * @param ast     A [[MapAlgebraAST]] which defines transformations over arbitrary rasters
    * @param source  A function from an [[RFMLRaster]] and z/x/y (tms) integers to possibly
    *                 existing tiles
    */
  def interpretTMS(
    ast: MapAlgebraAST,
    params: EvalParams,
    source: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]]
  )(implicit ec: ExecutionContext): (Int, Int, Int) => Future[Interpreted] = {
    // have to parse AST per-request because there is no structure to capture intermediate results
    (z: Int, x: Int, y: Int) => {

      def eval(ast: MapAlgebraAST): Future[Interpreted] = ast match {
        case Source(id, label) =>
          if (params.sources.isDefinedAt(id)) {
            val rfmlRaster = params.sources(id)
            source(rfmlRaster, z, x, y) map { maybeTile =>
              val maybeLazyTile = maybeTile.map { tile => LazyTile(tile) }
              Validated.fromOption(maybeLazyTile, { NonEmptyList.of(RasterRetrievalError(id, rfmlRaster.id)) })
            }
          } else {
            Future.successful { Invalid(NonEmptyList.of(MissingParameter(id))) }
          }

        // For the exhaustive match
        case op: Operation =>
          interpretOperation(op, eval)
      }

      eval(ast)
    }
  }
}
