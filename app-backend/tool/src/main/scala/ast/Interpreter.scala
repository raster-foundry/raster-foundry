package com.azavea.rf.tool.ast

import java.util.UUID

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import geotrellis.raster._
import geotrellis.raster.op._
import geotrellis.raster.render.{BreakMap, MapStrategy}
import scala.concurrent.{Future, ExecutionContext}
import cats.data._
import cats.implicits._

object Interpreter {
  def tms(
    ast: MapAlgebraAST,
    source: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]]
  )(implicit ec: ExecutionContext): (Int, Int, Int) => Future[Option[Op]] = {
    // have to parse AST per-request because there is no structure to capture intermediate results

    // Binary operation evaluation
    def evalB(
      futureTiles: Seq[Future[Option[Op]]],
      f: (Op, Op) => Op
    ): Future[Option[Op]] = {
      for (tiles <- Future.sequence(futureTiles)) yield {
        // wish there was Option.sequence
        val maybeList: Option[Seq[Op]] =
          if (tiles.forall(_.isDefined)) Some(tiles.flatten) else None
        maybeList.map(_.reduce(f))
      }
    }

    // Unary operation evaluation
    def evalU(
      futureTile: Future[Option[Op]],
      f: Op => Op
    ): Future[Option[Op]] = {
      for (tile <- futureTile) yield {
        val maybeTile = if (tile.isDefined) tile else None
        maybeTile.map(f)
      }
    }

    (z: Int, x: Int, y: Int) => {
      def eval(ast: MapAlgebraAST): Future[Option[Op]] = ast match {
        case RFMLRasterSource(id, label, rasterRef) =>
          OptionT.fromOption[Future](rasterRef).flatMapF { ref =>
            source(ref, z, x, y)
          }.map(Op.apply).value

        // For the exhaustive match
        case op: Operation => op match {
          case Addition(args, _, _) =>
            evalB(args.map(eval),  _ + _)

          case Subtraction(args, _, _) =>
            evalB(args.map(eval),  _ - _)

          case Multiplication(args, _, _) =>
            evalB(args.map(eval),  _ * _)

          case Division(args, _, _) =>
            evalB(args.map(eval),  _ / _)

          case Classification(args, _, _, breaks) =>
            evalU(eval(args.head), _.classify(breaks.toBreakMap))
        }
      }

      if (ast.evaluable) eval(ast)
      else Future.successful(None)
    }
  }
}
