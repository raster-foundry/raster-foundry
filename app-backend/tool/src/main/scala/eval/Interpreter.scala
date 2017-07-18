package com.azavea.rf.tool.eval

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

import cats._
import cats.data._
import cats.data.Validated._
import cats.implicits._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.params._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling._
import geotrellis.vector.{Extent, MultiPolygon}

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

  /** Does a given AST have at least one source? */
  private def hasSources[M: Monoid](ast: MapAlgebraAST): Interpreted[M] = {
    if (ast.sources.exists({ case x: Source => true; case _ => false })) Valid(Monoid.empty) else {
      Invalid(NonEmptyList.of(NoSourceLeaves(ast.id)))
    }
  }

  /** Interpret an AST with its matched execution parameters, but do so
    * without fetching any Rasters. Only interprets the structural validatity of
    * the AST, given the params.
    *
    * @param validReferences A boolean flag for determining whether `ToolReference` nodes are valid
    */
  def interpretPure[M: Monoid](
    ast: MapAlgebraAST,
    sourceMapping: Map[UUID, RFMLRaster],
    validReferences: Boolean
  ): Interpreted[M] = ast match {
    /* Validate leaf nodes */
    case Source(id, _) if sourceMapping.isDefinedAt(id) => Valid(Monoid.empty)
    case Source(id, _) => Invalid(NonEmptyList.of(MissingParameter(id)))
    case Constant(_, _, _) => Valid(Monoid.empty)
    case ToolReference(id, _) =>
      if (!validReferences) Invalid(NonEmptyList.of(UnsubstitutedRef(id)))
      else Valid(Monoid.empty)

    /* Unary operations must have only one arguments */
    case op: UnaryOperation => {
      /* Check for errors further down, first */
      val kids: Interpreted[M] = op.args.foldMap(a => interpretPure(a, sourceMapping, validReferences))

      /* Unary ops must only have one argument */
      val argLen: Interpreted[M] = if (op.args.length == 1) Valid(Monoid.empty) else {
        Invalid(NonEmptyList.of(IncorrectArgCount(op.id, 1, op.args.length)))
      }

      /* Combine these (potential) errors via their Semigroup instance */
      kids.combine(argLen).combine(hasSources(op))
    }

    /* All binary operations must have at least 2 arguments */
    case op: Operation => {
      val kids: Interpreted[M] = op.args.foldMap(a => interpretPure(a, sourceMapping, validReferences))

      val argLen: Interpreted[M] = if (op.args.length > 1) Valid(Monoid.empty) else {
        Invalid(NonEmptyList.of(IncorrectArgCount(op.id, 2, op.args.length)))
      }

      kids.combine(argLen)
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
    tileSource: RFMLRaster => Future[Option[TileWithNeighbors]]
  )(implicit ec: ExecutionContext): Future[Interpreted[LazyTile]] = {

    @SuppressWarnings(Array("TraversableHead"))
    def eval(tiles: Map[UUID, TileWithNeighbors], ast: MapAlgebraAST): LazyTile = ast match {

      /* --- LEAVES --- */
      case Source(id, _) => LazyTile(tiles(id).centerTile)
      case Constant(_, const, _) => LazyTile.Constant(const)
      case ToolReference(_, _) => sys.error("Attempt to evaluate a ToolReference!")

      /* --- LOCAL OPERATIONS --- */
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
        logger.debug(s"case masking at $id")
        eval(tiles, args.head).mask(extent, mask)
      case Equality(args, id, _) =>
        logger.debug(s"case equality at $id")
        args.map(eval(tiles, _)).reduce(_ == _)
      case Inequality(args, id, _) =>
        logger.debug(s"case inequality at $id")
        args.map(eval(tiles, _)).reduce(_ != _)
      case Greater(args, id, _) =>
        logger.debug(s"case greaterThan at $id")
        args.map(eval(tiles, _)).reduce(_ > _)
      case GreaterOrEqual(args, id, _) =>
        logger.debug(s"case greaterThanOrEqualTo at $id")
        args.map(eval(tiles, _)).reduce(_ >= _)
      case Less(args, id, _) =>
        logger.debug(s"case lessThan at $id")
        args.map(eval(tiles, _)).reduce(_ < _)
      case LessOrEqual(args, id, _) =>
        logger.debug(s"case lessThanOrEqualTo at $id")
        args.map(eval(tiles, _)).reduce(_ <= _)
      case And(args, id, _) =>
        logger.debug(s"case intersection/and at $id")
        args.map(eval(tiles, _)).reduce(_ and _)
      case Or(args, id, _) =>
        logger.debug(s"case union/or at $id")
        args.map(eval(tiles, _)).reduce(_ or _)
      case Xor(args, id, _) =>
        logger.debug(s"case xor at $id")
        args.map(eval(tiles, _)).reduce(_ xor _)
      case Pow(args, id, _) =>
        logger.debug(s"case pow at $id")
        args.map(eval(tiles, _)).reduce(_ ** _)
      case Atan2(args, id, _) =>
        logger.debug(s"case atan2 at $id")
        args.map(eval(tiles, _)).reduce(_ atan2 _)

      /* --- Unary Operations --- */
      case IsDefined(args, id, _) =>
        logger.debug(s"case defined at $id")
        eval(tiles, args.head).defined
      case IsUndefined(args, id, _) =>
        logger.debug(s"case undefined at $id")
        eval(tiles, args.head).undefined
      case SquareRoot(args, id, _) =>
        logger.debug(s"case sqrt at $id")
        eval(tiles, args.head).sqrt
      case Log(args, id, _) =>
        logger.debug(s"case log at $id")
        eval(tiles, args.head).log
      case Log10(args, id, _) =>
        logger.debug(s"case log10 at $id")
        eval(tiles, args.head).log10
      case Round(args, id, _) =>
        logger.debug(s"case round at $id")
        eval(tiles, args.head).round
      case Floor(args, id, _) =>
        logger.debug(s"case floor at $id")
        eval(tiles, args.head).floor
      case Ceil(args, id, _) =>
        logger.debug(s"case ceil at $id")
        eval(tiles, args.head).ceil
      case NumericNegation(args, id, _) =>
        logger.debug(s"case numeric negation at $id")
        eval(tiles, args.head).inverse
      case LogicalNegation(args, id, _) =>
        logger.debug(s"case logical negation at $id")
        eval(tiles, args.head).not
      case Abs(args, id, _) =>
        logger.debug(s"case abs at $id")
        eval(tiles, args.head).abs
      case Sin(args, id, _) =>
        logger.debug(s"case sin at $id")
        eval(tiles, args.head).sin
      case Cos(args, id, _) =>
        logger.debug(s"case cos at $id")
        eval(tiles, args.head).cos
      case Tan(args, id, _) =>
        logger.debug(s"case tan at $id")
        eval(tiles, args.head).tan
      case Sinh(args, id, _) =>
        logger.debug(s"case sinh at $id")
        eval(tiles, args.head).sinh
      case Cosh(args, id, _) =>
        logger.debug(s"case cosh at $id")
        eval(tiles, args.head).cosh
      case Tanh(args, id, _) =>
        logger.debug(s"case tanh at $id")
        eval(tiles, args.head).tanh
      case Asin(args, id, _) =>
        logger.debug(s"case asin at $id")
        eval(tiles, args.head).asin
      case Acos(args, id, _) =>
        logger.debug(s"case acos at $id")
        eval(tiles, args.head).acos
      case Atan(args, id, _) =>
        logger.debug(s"case atan at $id")
        eval(tiles, args.head).atan

      /* --- FOCAL OPERATIONS --- */
      case FocalMax(args, id, _, neighborhood) =>
        logger.debug(s"case focal maximum at $id")
        eval(tiles, args.head).focalMax(neighborhood, None)
      case FocalMin(args, id, _, neighborhood) =>
        logger.debug(s"case focal minimum at $id")
        eval(tiles, args.head).focalMin(neighborhood, None)
      case FocalMean(args, id, _, neighborhood) =>
        logger.debug(s"case focal mean at $id")
        eval(tiles, args.head).focalMean(neighborhood, None)
      case FocalMedian(args, id, _, neighborhood) =>
        logger.debug(s"case focal median at $id")
        eval(tiles, args.head).focalMedian(neighborhood, None)
      case FocalMode(args, id, _, neighborhood) =>
        logger.debug(s"case focal mode at $id")
        eval(tiles, args.head).focalMode(neighborhood, None)
      case FocalSum(args, id, _, neighborhood) =>
        logger.debug(s"case focal sum at $id")
        eval(tiles, args.head).focalSum(neighborhood, None)
      case FocalStdDev(args, id, _, neighborhood) =>
        logger.debug(s"case focal standard deviation at $id")
        eval(tiles, args.head).focalStdDev(neighborhood, None)

    }

    val pure: Interpreted[Unit] = interpretPure[Unit](ast, sourceMapping, false)
    val overridden: Interpreted[MapAlgebraAST] = overrideParams(ast, overrides)

    val bufferedSources = ast.bufferedSources()

    sourceMapping
      .mapValues(r => tileSource(r).map(_.toRight(r.id)).recover({ case t: Throwable => Left(r.id) }))
      .sequence
      .map({ sms =>
        val tiles: Interpreted[Map[UUID, TileWithNeighbors]] = sms.map({
          case (id, Left(rid)) => (id, Invalid(NonEmptyList.of(RasterRetrievalError(id, rid))))
          case (id, Right(tile)) => (id, Valid(tile))
        }).sequence

        (pure |@| hasSources(ast) |@| overridden |@| tiles).map({
          case (_, _, tree, ts) => eval(ts, tree)
        })
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
    tileSource: (RFMLRaster, Boolean, Int, Int, Int) => Future[Option[TileWithNeighbors]],
    expectedTileSize: Int
  )(implicit ec: ExecutionContext): (Int, Int, Int) => Future[Interpreted[LazyTile]] = {

    (z: Int, x: Int, y: Int) => {
      lazy val extent = layouts(z).mapTransform(SpatialKey(x,y))

      @SuppressWarnings(Array("TraversableHead"))
      def eval(tiles: Map[UUID, TileWithNeighbors], ast: MapAlgebraAST, buffer: Int): LazyTile = ast match {

        /* --- LEAVES --- */
        case Source(id, _) => LazyTile(tiles(id).withBuffer(buffer))
        case Constant(_, const, _) => LazyTile.Constant(const)
        case  ToolReference(_, _) => sys.error("TMS: Attempt to evaluate a ToolReference!")

        /* --- LOCAL OPERATIONS --- */
        case Addition(args, id, _) =>
          logger.debug(s"case addition at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ + _)
        case Subtraction(args, id, _) =>
          logger.debug(s"case subtraction at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ - _)
        case Multiplication(args, id, _) =>
          logger.debug(s"case multiplication at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ * _)
        case Division(args, id, _) =>
          logger.debug(s"case division at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ / _)
        case Max(args, id, _) =>
          logger.debug(s"case max at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ max _)
        case Min(args, id, _) =>
          logger.debug(s"case min at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ min _)
        case Classification(args, id, _, breaks) =>
          logger.debug(s"case classification at $id with breakmap ${breaks.toBreakMap}")
          eval(tiles, args.head, buffer).classify(breaks.toBreakMap)
        case Masking(args, id, _, mask) =>
          eval(tiles, args.head, buffer).mask(extent, mask)
        case Equality(args, id, _) =>
          logger.debug(s"case equality at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ == _)
        case Inequality(args, id, _) =>
          logger.debug(s"case inequality at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ != _)
        case Greater(args, id, _) =>
          logger.debug(s"case greaterThan at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ > _)
        case GreaterOrEqual(args, id, _) =>
          logger.debug(s"case greaterThanOrEqualTo at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ >= _)
        case Less(args, id, _) =>
          logger.debug(s"case lessThan at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ < _)
        case LessOrEqual(args, id, _) =>
          logger.debug(s"case lessThanOrEqualTo at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ <= _)
        case And(args, id, _) =>
          logger.debug(s"case intersection/and at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ and _)
        case Or(args, id, _) =>
          logger.debug(s"case union/or at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ or _)
        case Xor(args, id, _) =>
          logger.debug(s"case xor at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ xor _)
        case Pow(args, id, _) =>
          logger.debug(s"case pow at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ ** _)
        case Atan2(args, id, _) =>
          logger.debug(s"case atan2 at $id")
          args.map(eval(tiles, _, buffer)).reduce(_ atan2 _)

        /* --- Unary Operations --- */
        case IsDefined(args, id, _) =>
          logger.debug(s"case defined at $id")
          eval(tiles, args.head, buffer).defined
        case IsUndefined(args, id, _) =>
          logger.debug(s"case undefined at $id")
          eval(tiles, args.head, buffer).undefined
        case SquareRoot(args, id, _) =>
          logger.debug(s"case sqrt at $id")
          eval(tiles, args.head, buffer).sqrt
        case Log(args, id, _) =>
          logger.debug(s"case log at $id")
          eval(tiles, args.head, buffer).log
        case Log10(args, id, _) =>
          logger.debug(s"case log10 at $id")
          eval(tiles, args.head, buffer).log10
        case Round(args, id, _) =>
          logger.debug(s"case round at $id")
          eval(tiles, args.head, buffer).round
        case Floor(args, id, _) =>
          logger.debug(s"case floor at $id")
          eval(tiles, args.head, buffer).floor
        case Ceil(args, id, _) =>
          logger.debug(s"case ceil at $id")
          eval(tiles, args.head, buffer).ceil
        case NumericNegation(args, id, _) =>
          logger.debug(s"case numeric negation at $id")
          eval(tiles, args.head, buffer).inverse
        case LogicalNegation(args, id, _) =>
          logger.debug(s"case logical negation at $id")
          eval(tiles, args.head, buffer).not
        case Abs(args, id, _) =>
          logger.debug(s"case abs at $id")
          eval(tiles, args.head, buffer).abs
        case Sin(args, id, _) =>
          logger.debug(s"case sin at $id")
          eval(tiles, args.head, buffer).sin
        case Cos(args, id, _) =>
          logger.debug(s"case cos at $id")
          eval(tiles, args.head, buffer).cos
        case Tan(args, id, _) =>
          logger.debug(s"case tan at $id")
          eval(tiles, args.head, buffer).tan
        case Sinh(args, id, _) =>
          logger.debug(s"case sinh at $id")
          eval(tiles, args.head, buffer).sinh
        case Cosh(args, id, _) =>
          logger.debug(s"case cosh at $id")
          eval(tiles, args.head, buffer).cosh
        case Tanh(args, id, _) =>
          logger.debug(s"case tanh at $id")
          eval(tiles, args.head, buffer).tanh
        case Asin(args, id, _) =>
          logger.debug(s"case asin at $id")
          eval(tiles, args.head, buffer).asin
        case Acos(args, id, _) =>
          logger.debug(s"case acos at $id")
          eval(tiles, args.head, buffer).acos
        case Atan(args, id, _) =>
          logger.debug(s"case atan at $id")
          eval(tiles, args.head, buffer).atan

        /* --- FOCAL OPERATIONS --- */
        case FocalMax(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal maximum at $id with bounds $gridbounds")
          eval(tiles, args.head, buffer + n.extent)
            .focalMax(n, Some(gridbounds))
        case FocalMin(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal minimum at $id with bounds $gridbounds")
          eval(tiles, args.head, buffer + n.extent)
            .focalMin(n, Some(gridbounds))
        case FocalMean(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal mean at $id with bounds $gridbounds")
          eval(tiles, args.head, buffer + n.extent)
            .focalMean(n, Some(gridbounds))
        case FocalMedian(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal median at $id with bounds $gridbounds")
          eval(tiles, args.head, buffer + n.extent)
            .focalMedian(n, Some(gridbounds))
        case FocalMode(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal mode at $id with bounds $gridbounds")
          eval(tiles, args.head, buffer + n.extent)
            .focalMode(n, Some(gridbounds))
        case FocalSum(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal sum at $id with bounds $gridbounds")
          eval(tiles, args.head, buffer + n.extent)
            .focalSum(n, Some(gridbounds))
        case FocalStdDev(args, id, _, n) =>
          val gridbounds = GridBounds(n.extent, n.extent, expectedTileSize - 1 + buffer * 2 + n.extent, expectedTileSize - 1 + buffer * 2 + n.extent)
          logger.debug(s"case focal standard deviation at $id with bounds $gridbounds")
          eval(tiles, args.head, buffer + n.extent)
            .focalStdDev(n, Some(gridbounds))
      }

      val pure: Interpreted[Unit] = interpretPure[Unit](ast, sourceMapping, false)
      val overridden: Interpreted[MapAlgebraAST] = overrideParams(ast, overrides)

      val bufferedSources = ast.bufferedSources()

      sourceMapping
        .map({ case (nodeId, rfml) =>
            (nodeId -> tileSource(rfml, bufferedSources.contains(nodeId), z, x, y)
              .map(_.toRight(rfml.id))
              .recover({ case t: Throwable => Left(rfml.id) }))
        })
        .sequence
        .map({ sms =>
          val tiles: Interpreted[Map[UUID, TileWithNeighbors]] = sms.map({
            case (id, Left(rid)) => (id, Invalid(NonEmptyList.of(RasterRetrievalError(id, rid))))
            case (id, Right(tile)) => (id, Valid(tile))
          }).sequence

          (pure |@| overridden |@| tiles).map({ case (_, tree, ts) => eval(ts, tree, 0) })
        })
    }
  }
}
