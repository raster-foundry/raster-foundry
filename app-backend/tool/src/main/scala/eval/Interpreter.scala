package com.azavea.rf.tool.eval

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.params._

import cats._
import cats.data._
import cats.data.Validated._
import cats.implicits._
import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.raster.crop.Crop
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling._
import geotrellis.vector.{Extent, MultiPolygon}

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID
import java.security.InvalidParameterException


/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
object Interpreter extends LazyLogging {

  /** The Interpreted type is either a list of failures or a compiled MapAlgebra operation */
  type Interpreted[A] = ValidatedNel[InterpreterError, A]

  val layouts: Array[LayoutDefinition] = (0 to 30).map(n =>
    ZoomedLayoutScheme.layoutForZoom(n, WebMercator.worldExtent, 256)
  ).toArray

  def overrideParams(
    ast: MapAlgebraAST,
    overrides: Map[UUID, ParamOverride]
  ): Interpreted[MapAlgebraAST] = ast match {
    /* Can't override data Sources */
    case s: Source => Valid(s)
    case t: ToolReference => Valid(t)

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

    case Masking(args, id, meta, mask) => {
      val kids: Interpreted[List[MapAlgebraAST]] =
        args.map(a => overrideParams(a, overrides)).sequence

      val newMask: Interpreted[MultiPolygon] = overrides.get(id) match {
        case None => Valid(mask)
        case Some(ParamOverride.Masking(m)) => Valid(m)
        case Some(_) => Invalid(NonEmptyList.of(InvalidOverride(id)))
      }

      (kids |@| newMask).map({ case (ks, m) => Masking(ks, id, meta, m) })
    }

    /* Non-overridable Operations */
    case o: Operation => o.args.map(a => overrideParams(a, overrides)).sequence.map(ks => o.withArgs(ks))
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
    case ToolReference(id, _) => Invalid(NonEmptyList.of(UnsubstitutedRef(id)))

    /* Unary operations must have only one arguments */
    case op: UnaryOperation => {
      /* Check for errors further down, first */
      val kids: Interpreted[M] = op.args.foldMap(a => interpretPure(a, sourceMapping))

      if (op.args.length == 1) kids else {
        /* Add this error to any from lower down via their Semigroup instance */
        Invalid(NonEmptyList.of(IncorrectArgCount(op.id, 1, op.args.length))).combine(kids)
      }
    }

    /* All binary operations must have at least 2 arguments */
    case op: Operation => {
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
    extent: Extent,
    source: RFMLRaster => Future[Option[Tile]]
  )(implicit ec: ExecutionContext): Future[Interpreted[LazyTile]] = {

    @SuppressWarnings(Array("TraversableHead"))
    def eval(tiles: Map[UUID, Tile], ast: MapAlgebraAST): LazyTile = ast match {

      /* --- LEAVES --- */
      case Source(id, _) => LazyTile(tiles(id))

      case Constant(_, const, _) => LazyTile.Constant(const)

      case  ToolReference(_, _) => sys.error("TMS: Attempt to evaluate a ToolReference!")

      /* --- OPERATIONS --- */
      case Addition(args, id, _) =>
        logger.debug(s"case addition at $id")
        args.map(eval(tiles, _)).reduce(_ + _)

      case Subtraction(args, id, _) =>
        logger.debug(s"case subtraction at $id")
        args.map(eval(tiles, _)).reduce(_ - _)

      case Multiplication(args, id, _) =>
        logger.debug(s"case multiplication at $id")
        args.map(eval(tiles, _)).reduce(_ * _)

      case Division(args, id, _) =>
        logger.debug(s"case division at $id")
        args.map(eval(tiles, _)).reduce(_ / _)

      case Max(args, id, _) =>
        logger.debug(s"case max at $id")
        args.map(eval(tiles, _)).reduce(_ max _)

      case Min(args, id, _) =>
        logger.debug(s"case min at $id")
        args.map(eval(tiles, _)).reduce(_ min _)

      case Classification(args, id, _, breaks) =>
        logger.debug(s"case classification at $id with breakmap ${breaks.toBreakMap}")
        eval(tiles, args.head).classify(breaks.toBreakMap)

      case Masking(args, id, _, mask) =>
        eval(tiles, args.head).mask(extent, mask)
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
    source: (RFMLRaster, Int, Int, Int) => Future[Option[Tile]],
    buffer: Int = 0
  )(implicit ec: ExecutionContext): (Int, Int, Int) => Future[Interpreted[LazyTile]] = {

    (z: Int, x: Int, y: Int) => {
      val extent = layouts(z).mapTransform(SpatialKey(x,y))

      @SuppressWarnings(Array("TraversableHead"))
      def eval(tiles: Map[UUID, (Int, Int, Int) => Future[Interpreted[Tile]]], ast: MapAlgebraAST, buffer: Int = buffer): Future[Interpreted[LazyTile]] = ast match {
        /* --- LEAVES --- */
        case Source(id, _) =>
          println("SOURCE!", buffer)
          val resolver: (Int, Int, Int) => Future[Interpreted[Tile]] = tiles(id)
          if (buffer > 255)
            Future.successful { Invalid(NonEmptyList.of(ExcessiveFocalBuffer(id, buffer))) }
          else if (buffer > 0) {
            for {
              tl <- resolver(z, x-1, y+1)
              tm <- resolver(z, x,   y+1)
              tr <- resolver(z, x+1, y+1)
              ml <- resolver(z, x-1, y)
              mm <- resolver(z, x,   y)
              mr <- resolver(z, x+1, y)
              bl <- resolver(z, x-1, y-1)
              bm <- resolver(z, x,   y-1)
              br <- resolver(z, x+1, y-1)
            } yield {
              (tl |@| tm |@| tr |@| ml |@| mm |@| mr |@| bl |@| bm |@| br).map({ case (t1, t2, t3, m1, m2, m3, b1, b2, b3) =>
                val tile = CompositeTile(Array(t1, t2, t3, m1, m2, m3, b1, b2, b3), TileLayout(3, 3, 256, 256))
                  .crop(GridBounds(256 - buffer, 512 + buffer, 256 - buffer, 512 + buffer), Crop.Options.DEFAULT)
                LazyTile(tile)
              })
            }
          }
          else
            tiles(id)(z, x, y).map({ interpreted =>
              interpreted.map({ tile => LazyTile(tile) })
            })

        case Constant(_, const, _) =>
          Future.successful { Valid(LazyTile.Constant(const)) }

        case  ToolReference(_, _) =>
          sys.error("TMS: Attempt to evaluate a ToolReference!")

        /* --- FOCAL OPERATIONS --- */
        case FocalMax(args, id, _, neighborhood) =>
          eval(tiles, args.head).map(_.map(_.focalMax(neighborhood)))

        /* --- LOCAL OPERATIONS --- */
        case Addition(args, id, _) =>
          logger.debug(s"case addition at $id")
          args.map(eval(tiles, _)).sequence.map(_.sequence.map(_.reduce(_ + _)))

        case Subtraction(args, id, _) =>
          logger.debug(s"case subtraction at $id")
          args.map(eval(tiles, _)).sequence.map(_.sequence.map(_.reduce(_ - _)))

        case Multiplication(args, id, _) =>
          logger.debug(s"case multiplication at $id")
          args.map(eval(tiles, _)).sequence.map(_.sequence.map(_.reduce(_ * _)))

        case Division(args, id, _) =>
          logger.debug(s"case division at $id")
          args.map(eval(tiles, _)).sequence.map(_.sequence.map(_.reduce(_ / _)))

        case Max(args, id, _) =>
          logger.debug(s"case max at $id")
          args.map(eval(tiles, _)).sequence.map(_.sequence.map(_.reduce(_ max _)))

        case Min(args, id, _) =>
          logger.debug(s"case min at $id")
          args.map(eval(tiles, _)).sequence.map(_.sequence.map(_.reduce(_ min _)))

        case Classification(args, id, _, breaks) =>
          logger.debug(s"case classification at $id with breakmap ${breaks.toBreakMap}")
          eval(tiles, args.head).map(_.map(_.classify(breaks.toBreakMap)))

        case Masking(args, id, _, mask) =>
          eval(tiles, args.head).map(_.map(_.mask(extent, mask)))
      }

      val pure: Interpreted[Unit] = interpretPure[Unit](ast, sourceMapping)
      val overridden: Interpreted[MapAlgebraAST] = overrideParams(ast, overrides)

      val tiles: Map[UUID, (Int, Int, Int) => Future[Interpreted[Tile]]] =
        sourceMapping.foldLeft(Map[UUID, (Int, Int, Int) => Future[Interpreted[Tile]]]())({ case (map, (nodeId, rfmlRaster)) =>
        val func: (Int, Int, Int) => Future[Interpreted[Tile]] = { (z: Int, x: Int, y: Int) =>
            source(rfmlRaster, z, x, y).map {
              case Some(tile) => Valid(tile)
              case None => Invalid(NonEmptyList.of(RasterRetrievalError(nodeId, rfmlRaster.id)))
            }
          }
          map + (nodeId -> func)
        })

      (pure |@| overridden) match {
        case (_, Valid(tree)) => eval(tiles, tree)
        case (_, Invalid(i)) => Future.successful { Invalid(i) }
      }
    }
  }
}
