package com.azavea.rf.tool.eval

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

import cats._
import cats.data.{NonEmptyList => NEL, _}
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

import scala.util.Try


/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
object BufferingInterpreter extends LazyLogging {

  val layouts: Array[LayoutDefinition] = (0 to 30).map(n =>
    ZoomedLayoutScheme.layoutForZoom(n, WebMercator.worldExtent, 256)
  ).toArray

  def literalize(
    ast: MapAlgebraAST,
    tileSource: (RFMLRaster, Boolean, Int, Int, Int) => Future[Interpreted[TileWithNeighbors]],
    z: Int,
    x: Int,
    y: Int
  )(implicit ec: ExecutionContext): Future[Interpreted[MapAlgebraAST]] = {

    def eval(ast: MapAlgebraAST, buffer: Int): Future[Interpreted[MapAlgebraAST]] =
      Try({
        ast match {
          case sr@SceneRaster(_, sceneId, band, celltype, md) =>
            if (buffer > 0)
              tileSource(sr, true, z, x, y).map({ interp =>
                interp.map({ tile =>
                  LiteralRaster(sceneId, tile.withBuffer(buffer), md)
                })
              })
            else
              tileSource(sr, true, z, x, y).map({ interp =>
                interp.map({ tile =>
                  LiteralRaster(sceneId, tile.centerTile, md)
                })
              })
          case pr@ProjectRaster(_, projId, band, celltype, md) =>
            if (buffer > 0)
              tileSource(pr, true, z, x, y).map({ interp =>
                interp.map({ tile =>
                  LiteralRaster(projId, tile.withBuffer(buffer), md)
                })
              })
            else
              tileSource(pr, true, z, x, y).map({ interp =>
                interp.map({ tile =>
                  LiteralRaster(projId, tile.centerTile, md)
                })
              })
          case f: FocalOperation =>
            ast.args.map(eval(_, buffer + f.neighborhood.extent))
              .sequence
              .map(_.sequence)
              .map({
                case Valid(args) => Valid(ast.withArgs(args))
                case i@Invalid(_) => i
              })
          case _ =>
            ast.args.map(eval(_, buffer))
              .sequence
              .map(_.sequence)
              .map({
                case Valid(args) => Valid(ast.withArgs(args))
                case i@Invalid(_) => i
              })
        }
      }).getOrElse(Future.successful(Invalid(NEL.of(RasterRetrievalError(ast)))))

    val pure: Interpreted[Unit] = PureInterpreter.interpret[Unit](ast, false)

    // Aggregate multiple errors here...
    eval(ast, 0).map({ res =>
      res.leftMap({ errors =>
        pure match {
          case Invalid(e) => e ++ errors.toList
          case _ => errors
        }
      })
    })
  }


  /** The Interpreter method for producing z/x/y TMS tiles
    *
    * @param ast     A [[MapAlgebraAST]] which defines transformations over arbitrary rasters
    * @param source  A function from an [[RFMLRaster]] and z/x/y (tms) integers to possibly
    *                 existing tiles
    */
  def interpret(
    ast: MapAlgebraAST,
    expectedTileSize: Int
  )(implicit ec: ExecutionContext): (Int, Int, Int) => Interpreted[LazyTile] = {

    (z: Int, x: Int, y: Int) => {
      lazy val extent = layouts(z).mapTransform(SpatialKey(x,y))

      @SuppressWarnings(Array("TraversableHead"))
      def eval(ast: MapAlgebraAST, buffer: Int): LazyTile = ast match {

        /* --- LEAVES --- */
        case Source(_, _) => sys.error("TMS: Attempt to evaluate a ToolReference!")
        case ToolReference(_, _) => sys.error("TMS: Attempt to evaluate a ToolReference!")
        case SceneRaster(_, _, _, _, _) => sys.error("TMS: Attempt to evaluate a SceneRaster!")
        case ProjectRaster(_, _, _, _, _) => sys.error("TMS: Attempt to evaluate a ProjectRaster!")
        case LiteralRaster(_, lt, _) => lt
        case Constant(_, const, _) => LazyTile.Constant(const)
        /* --- LOCAL OPERATIONS --- */
        case Addition(args, id, _) =>
          logger.debug(s"case addition at $id")
          args.map(eval(_, buffer)).reduce(_ + _)
        case Subtraction(args, id, _) =>
          logger.debug(s"case subtraction at $id")
          args.map(eval(_, buffer)).reduce(_ - _)
        case Multiplication(args, id, _) =>
          logger.debug(s"case multiplication at $id")
          args.map(eval(_, buffer)).reduce(_ * _)
        case Division(args, id, _) =>
          logger.debug(s"case division at $id")
          args.map(eval(_, buffer)).reduce(_ / _)
        case Max(args, id, _) =>
          logger.debug(s"case max at $id")
          args.map(eval(_, buffer)).reduce(_ max _)
        case Min(args, id, _) =>
          logger.debug(s"case min at $id")
          args.map(eval(_, buffer)).reduce(_ min _)
        case Classification(args, id, _, breaks) =>
          logger.debug(s"case classification at $id with breakmap ${breaks.toBreakMap}")
          eval(args.head, buffer).classify(breaks.toBreakMap)
        case Masking(args, id, _, mask) =>
          eval(args.head, buffer).mask(extent, mask)
        case Equality(args, id, _) =>
          logger.debug(s"case equality at $id")
          args.map(eval(_, buffer)).reduce(_ == _)
        case Inequality(args, id, _) =>
          logger.debug(s"case inequality at $id")
          args.map(eval(_, buffer)).reduce(_ != _)
        case Greater(args, id, _) =>
          logger.debug(s"case greaterThan at $id")
          args.map(eval(_, buffer)).reduce(_ > _)
        case GreaterOrEqual(args, id, _) =>
          logger.debug(s"case greaterThanOrEqualTo at $id")
          args.map(eval(_, buffer)).reduce(_ >= _)
        case Less(args, id, _) =>
          logger.debug(s"case lessThan at $id")
          args.map(eval(_, buffer)).reduce(_ < _)
        case LessOrEqual(args, id, _) =>
          logger.debug(s"case lessThanOrEqualTo at $id")
          args.map(eval(_, buffer)).reduce(_ <= _)
        case And(args, id, _) =>
          logger.debug(s"case intersection/and at $id")
          args.map(eval(_, buffer)).reduce(_ and _)
        case Or(args, id, _) =>
          logger.debug(s"case union/or at $id")
          args.map(eval(_, buffer)).reduce(_ or _)
        case Xor(args, id, _) =>
          logger.debug(s"case xor at $id")
          args.map(eval(_, buffer)).reduce(_ xor _)
        case Pow(args, id, _) =>
          logger.debug(s"case pow at $id")
          args.map(eval(_, buffer)).reduce(_ ** _)
        case Atan2(args, id, _) =>
          logger.debug(s"case atan2 at $id")
          args.map(eval(_, buffer)).reduce(_ atan2 _)

        /* --- Unary Operations --- */
        case IsDefined(args, id, _) =>
          logger.debug(s"case defined at $id")
          eval(args.head, buffer).defined
        case IsUndefined(args, id, _) =>
          logger.debug(s"case undefined at $id")
          eval(args.head, buffer).undefined
        case SquareRoot(args, id, _) =>
          logger.debug(s"case sqrt at $id")
          eval(args.head, buffer).sqrt
        case Log(args, id, _) =>
          logger.debug(s"case log at $id")
          eval(args.head, buffer).log
        case Log10(args, id, _) =>
          logger.debug(s"case log10 at $id")
          eval(args.head, buffer).log10
        case Round(args, id, _) =>
          logger.debug(s"case round at $id")
          eval(args.head, buffer).round
        case Floor(args, id, _) =>
          logger.debug(s"case floor at $id")
          eval(args.head, buffer).floor
        case Ceil(args, id, _) =>
          logger.debug(s"case ceil at $id")
          eval(args.head, buffer).ceil
        case NumericNegation(args, id, _) =>
          logger.debug(s"case numeric negation at $id")
          eval(args.head, buffer).inverse
        case LogicalNegation(args, id, _) =>
          logger.debug(s"case logical negation at $id")
          eval(args.head, buffer).not
        case Abs(args, id, _) =>
          logger.debug(s"case abs at $id")
          eval(args.head, buffer).abs
        case Sin(args, id, _) =>
          logger.debug(s"case sin at $id")
          eval(args.head, buffer).sin
        case Cos(args, id, _) =>
          logger.debug(s"case cos at $id")
          eval(args.head, buffer).cos
        case Tan(args, id, _) =>
          logger.debug(s"case tan at $id")
          eval(args.head, buffer).tan
        case Sinh(args, id, _) =>
          logger.debug(s"case sinh at $id")
          eval(args.head, buffer).sinh
        case Cosh(args, id, _) =>
          logger.debug(s"case cosh at $id")
          eval(args.head, buffer).cosh
        case Tanh(args, id, _) =>
          logger.debug(s"case tanh at $id")
          eval(args.head, buffer).tanh
        case Asin(args, id, _) =>
          logger.debug(s"case asin at $id")
          eval(args.head, buffer).asin
        case Acos(args, id, _) =>
          logger.debug(s"case acos at $id")
          eval(args.head, buffer).acos
        case Atan(args, id, _) =>
          logger.debug(s"case atan at $id")
          eval(args.head, buffer).atan

        /* --- FOCAL OPERATIONS --- */
        case FocalMax(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal maximum at $id with bounds $gridbounds")
          eval(args.head, buffer + n.extent)
            .focalMax(n, Some(gridbounds))
        case FocalMin(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal minimum at $id with bounds $gridbounds")
          eval(args.head, buffer + n.extent)
            .focalMin(n, Some(gridbounds))
        case FocalMean(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal mean at $id with bounds $gridbounds")
          eval(args.head, buffer + n.extent)
            .focalMean(n, Some(gridbounds))
        case FocalMedian(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal median at $id with bounds $gridbounds")
          eval(args.head, buffer + n.extent)
            .focalMedian(n, Some(gridbounds))
        case FocalMode(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal mode at $id with bounds $gridbounds")
          eval(args.head, buffer + n.extent)
            .focalMode(n, Some(gridbounds))
        case FocalSum(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal sum at $id with bounds $gridbounds")
          eval(args.head, buffer + n.extent)
            .focalSum(n, Some(gridbounds))
        case FocalStdDev(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal standard deviation at $id with bounds $gridbounds")
          eval(args.head, buffer + n.extent)
            .focalStdDev(n, Some(gridbounds))
      }

      val pure: Interpreted[Unit] = PureInterpreter.interpret[Unit](ast, false)

      pure.map({ case _ => eval(ast, 0) })
    }
  }
}
