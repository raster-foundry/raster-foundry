package com.azavea.rf.tool.op

import java.util.UUID

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import geotrellis.raster._
import geotrellis.raster.op._
import geotrellis.raster.render.{BreakMap, MapStrategy}
import cats.data._
import cats.data.Validated._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import java.lang.IllegalStateException
import java.net.URI



/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
object Interpreter extends LazyLogging {

  /** The Interpreted type is either a list of failures or a compiled MapAlgebra operation */
  type Interpreted = ValidatedNel[InterpreterError, Op]

  /** The primary method of this Interpreter
    *
    * @param ast     A [[MapAlgebraAST]] which defines transformations over arbitrary rasters
    * @param source  A function from an [[RFMLRaster]] and z/x/y (tms) integers to possibly
    *                 existing tiles
    */
  def tms(
    ast: MapAlgebraAST,
    source: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]]
  )(implicit ec: ExecutionContext): (Int, Int, Int) => Future[Interpreted] = {
    // have to parse AST per-request because there is no structure to capture intermediate results

    // Binary operation evaluation
    def evalBinary(
      futureTiles: Seq[Future[Interpreted]],
      f: (Op, Op) => Op
    ): Future[Interpreted] = {
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
      f: Op => Op
    ): Future[Interpreted] = {
      logger.debug("evalUnary")
      for (interpreted <- futureTile) yield {
        interpreted match {
          case Valid(tileOp) =>
            Valid(f(tileOp))
          case errors@Invalid(_) =>
            logger.debug(s"unary failure on $interpreted")
            errors
        }
      }
    }

    (z: Int, x: Int, y: Int) => {
      logger.debug(s"resolving tile at z: $z, x: $x, y: $y")
      def eval(ast: MapAlgebraAST): Future[Interpreted] = ast match {
        case RFMLRasterSource(id, label, None) =>
          logger.debug(s"case unbound rastersource at $id")
          Future.successful { Invalid(NonEmptyList.of(MissingParameter(id))) }

        case RFMLRasterSource(id, label, Some(ref)) =>
          logger.debug(s"case bound rastersource at $id")
          source(ref, z, x, y).map { maybeTile =>
            val maybeOp = maybeTile.map(Op.apply)
            Validated.fromOption(maybeOp, { NonEmptyList.of(RasterRetrievalError(id)) })
          }

        // For the exhaustive match
        case op: Operation =>
          op match {
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
      }

      eval(ast)
    }
  }
}
