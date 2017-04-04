package com.azavea.rf.tool.op

import java.util.UUID

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import geotrellis.raster._
import geotrellis.raster.op._
import geotrellis.raster.render.{BreakMap, MapStrategy}
import cats._, cats.data._, cats.implicits._
import cats._
import cats.data._
import cats.implicits._
import Validated._

import scala.concurrent.{Future, ExecutionContext}
import java.lang.IllegalStateException
import java.net.URI


object Interpreter {

  type Interpreted = ValidatedNel[InterpreterError, Op]
  def valid(op: Op): Interpreted =
    valid(op)
  def invalid(error: InterpreterError): Interpreted =
    invalid(error)

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
      def applyB(outInterpreted: Future[Interpreted], sourceInterpreted: Future[Interpreted]): Future[Interpreted] = {
        for {
          in <- sourceInterpreted
          out <- outInterpreted
        } yield {
          (in, out) match {
            case (Valid(inOp), Valid(outOp)) => valid(f(inOp, outOp))
            case (Invalid(e1), Invalid(e2)) => Invalid(e1 concat e2)
            case (_, errors@Invalid(_)) => errors
            case (errors@Invalid(_), _) => errors
          }
        }
      }
      futureTiles.reduce(applyB)
    }
    //      ast.unbound.reduceLeft { case(interpreted, subAst) =>
    //        interpreted |@| MissingParameter(subAst.id)
    //  for (tiles <- Future.sequence(futureTiles)) yield {
    //    // wish there was Option.sequence
    //    val maybeList: Option[Seq[Op]] =
    //      if (tiles.forall(_.isDefined)) Some(tiles.flatten) else None
    //    maybeList.map(_.reduce(f))
    //  }
    //}

    // Unary operation evaluation
    def evalUnary(
      futureTile: Future[Interpreted],
      f: Op => Op
    ): Future[Interpreted] = {
      for (interpreted <- futureTile) yield {
        interpreted match {
          case Valid(tile) =>
            valid(f(tile))
          case errors@Invalid(_) =>
            errors
        }
      }
    }

    (z: Int, x: Int, y: Int) => {
      def eval(ast: MapAlgebraAST): Future[Interpreted] = ast match {
        case RFMLRasterSource(id, label, rasterRef) =>
          require(rasterRef.isDefined, "Expectation of fully bound parameters violated")
          val ref = rasterRef.get
          source(ref, z, x, y).map { maybeTile =>
            val maybeOp = maybeTile.map(Op.apply)
            Validated.fromOption(maybeOp, { NonEmptyList.of(TileRetrievalError(ref.id, new URI(""))) })
          }

        // For the exhaustive match
        case op: Operation => op match {
          case Addition(args, _, _) =>
            evalBinary(args.map(eval),  _ + _)

          case Subtraction(args, _, _) =>
            evalBinary(args.map(eval),  _ - _)

          case Multiplication(args, _, _) =>
            evalBinary(args.map(eval),  _ * _)

          case Division(args, _, _) =>
            evalBinary(args.map(eval),  _ / _)

          case Classification(args, _, _, breaks) =>
            evalUnary(eval(args.head), _.classify(breaks.toBreakMap))
        }
      }

      if (ast.evaluable)
        eval(ast)
      else {
        Future.successful {
          var failures = ast.unbound.map { subAst: MapAlgebraAST => MissingParameter(subAst.id) }
          NonEmptyList.fromList(failures) match {
            case Some(nel) =>
              Invalid(nel)
            case None =>
              throw new IllegalStateException("Unable to produce list of unbound variables")
          }
        }
      }
    }
  }
}
