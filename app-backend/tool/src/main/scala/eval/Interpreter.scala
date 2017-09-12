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
object Interpreter extends LazyLogging {

  val layouts: Array[LayoutDefinition] = (0 to 30).map(n =>
    ZoomedLayoutScheme.layoutForZoom(n, WebMercator.worldExtent, 256)
  ).toArray

  /** Does a given AST have at least one source? */
  private def hasSources[M: Monoid](ast: MapAlgebraAST): Interpreted[M] = {
    if (ast.sources.exists({ case x: Source => true; case _ => false })) Valid(Monoid.empty) else {
      Invalid(NonEmptyList.of(NoSourceLeaves(ast.id)))
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
    extent: Extent
  )(implicit ec: ExecutionContext): Interpreted[LazyTile] = {

    @SuppressWarnings(Array("TraversableHead"))
    def eval(ast: MapAlgebraAST): LazyTile = ast match {
      /* --- LEAVES --- */
      case Source(id, _) => sys.error("Attempt to evaluate a variable node!")
      case Constant(_, const, _) => LazyTile.Constant(const)
      case ToolReference(_, _) => sys.error("Attempt to evaluate a ToolReference!")

      /* --- LOCAL OPERATIONS --- */
      case Addition(args, id, _) =>
        logger.debug(s"case addition at $id")
        args.map(eval(_)).reduce(_ + _)
      case Subtraction(args, id, _) =>
        logger.debug(s"case subtraction at $id")
        args.map(eval(_)).reduce(_ - _)
      case Multiplication(args, id, _) =>
        logger.debug(s"case multiplication at $id")
        args.map(eval(_)).reduce(_ * _)
      case Division(args, id, _) =>
        logger.debug(s"case division at $id")
        args.map(eval(_)).reduce(_ / _)
      case Max(args, id, _) =>
        logger.debug(s"case max at $id")
        args.map(eval(_)).reduce(_ max _)
      case Min(args, id, _) =>
        logger.debug(s"case min at $id")
        args.map(eval(_)).reduce(_ min _)
      case Classification(args, id, _, breaks) =>
        logger.debug(s"case classification at $id with breakmap ${breaks.toBreakMap}")
        eval(args.head).classify(breaks.toBreakMap)
      case Masking(args, id, _, mask) =>
        logger.debug(s"case masking at $id")
        eval(args.head).mask(extent, mask)
      case Equality(args, id, _) =>
        logger.debug(s"case equality at $id")
        args.map(eval(_)).reduce(_ == _)
      case Inequality(args, id, _) =>
        logger.debug(s"case inequality at $id")
        args.map(eval(_)).reduce(_ != _)
      case Greater(args, id, _) =>
        logger.debug(s"case greaterThan at $id")
        args.map(eval(_)).reduce(_ > _)
      case GreaterOrEqual(args, id, _) =>
        logger.debug(s"case greaterThanOrEqualTo at $id")
        args.map(eval(_)).reduce(_ >= _)
      case Less(args, id, _) =>
        logger.debug(s"case lessThan at $id")
        args.map(eval(_)).reduce(_ < _)
      case LessOrEqual(args, id, _) =>
        logger.debug(s"case lessThanOrEqualTo at $id")
        args.map(eval(_)).reduce(_ <= _)
      case And(args, id, _) =>
        logger.debug(s"case intersection/and at $id")
        args.map(eval(_)).reduce(_ and _)
      case Or(args, id, _) =>
        logger.debug(s"case union/or at $id")
        args.map(eval(_)).reduce(_ or _)
      case Xor(args, id, _) =>
        logger.debug(s"case xor at $id")
        args.map(eval(_)).reduce(_ xor _)
      case Pow(args, id, _) =>
        logger.debug(s"case pow at $id")
        args.map(eval(_)).reduce(_ ** _)
      case Atan2(args, id, _) =>
        logger.debug(s"case atan2 at $id")
        args.map(eval(_)).reduce(_ atan2 _)

      /* --- Unary Operations --- */
      case IsDefined(args, id, _) =>
        logger.debug(s"case defined at $id")
        eval(args.head).defined
      case IsUndefined(args, id, _) =>
        logger.debug(s"case undefined at $id")
        eval(args.head).undefined
      case SquareRoot(args, id, _) =>
        logger.debug(s"case sqrt at $id")
        eval(args.head).sqrt
      case Log(args, id, _) =>
        logger.debug(s"case log at $id")
        eval(args.head).log
      case Log10(args, id, _) =>
        logger.debug(s"case log10 at $id")
        eval(args.head).log10
      case Round(args, id, _) =>
        logger.debug(s"case round at $id")
        eval(args.head).round
      case Floor(args, id, _) =>
        logger.debug(s"case floor at $id")
        eval(args.head).floor
      case Ceil(args, id, _) =>
        logger.debug(s"case ceil at $id")
        eval(args.head).ceil
      case NumericNegation(args, id, _) =>
        logger.debug(s"case numeric negation at $id")
        eval(args.head).inverse
      case LogicalNegation(args, id, _) =>
        logger.debug(s"case logical negation at $id")
        eval(args.head).not
      case Abs(args, id, _) =>
        logger.debug(s"case abs at $id")
        eval(args.head).abs
      case Sin(args, id, _) =>
        logger.debug(s"case sin at $id")
        eval(args.head).sin
      case Cos(args, id, _) =>
        logger.debug(s"case cos at $id")
        eval(args.head).cos
      case Tan(args, id, _) =>
        logger.debug(s"case tan at $id")
        eval(args.head).tan
      case Sinh(args, id, _) =>
        logger.debug(s"case sinh at $id")
        eval(args.head).sinh
      case Cosh(args, id, _) =>
        logger.debug(s"case cosh at $id")
        eval(args.head).cosh
      case Tanh(args, id, _) =>
        logger.debug(s"case tanh at $id")
        eval(args.head).tanh
      case Asin(args, id, _) =>
        logger.debug(s"case asin at $id")
        eval(args.head).asin
      case Acos(args, id, _) =>
        logger.debug(s"case acos at $id")
        eval(args.head).acos
      case Atan(args, id, _) =>
        logger.debug(s"case atan at $id")
        eval(args.head).atan

      /* --- FOCAL OPERATIONS --- */
      case FocalMax(args, id, _, neighborhood) =>
        logger.debug(s"case focal maximum at $id")
        eval(args.head).focalMax(neighborhood, None)
      case FocalMin(args, id, _, neighborhood) =>
        logger.debug(s"case focal minimum at $id")
        eval(args.head).focalMin(neighborhood, None)
      case FocalMean(args, id, _, neighborhood) =>
        logger.debug(s"case focal mean at $id")
        eval(args.head).focalMean(neighborhood, None)
      case FocalMedian(args, id, _, neighborhood) =>
        logger.debug(s"case focal median at $id")
        eval(args.head).focalMedian(neighborhood, None)
      case FocalMode(args, id, _, neighborhood) =>
        logger.debug(s"case focal mode at $id")
        eval(args.head).focalMode(neighborhood, None)
      case FocalSum(args, id, _, neighborhood) =>
        logger.debug(s"case focal sum at $id")
        eval(args.head).focalSum(neighborhood, None)
      case FocalStdDev(args, id, _, neighborhood) =>
        logger.debug(s"case focal standard deviation at $id")
        eval(args.head).focalStdDev(neighborhood, None)

    }

    val pure: Interpreted[Unit] = PureInterpreter.interpretPure[Unit](ast, false)
    pure.map({ case _ => eval(ast) })
  }
}

