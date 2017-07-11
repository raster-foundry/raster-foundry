package com.azavea.rf.batch

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.eval.Interpreter.Interpreted
import com.azavea.rf.tool.params.ParamOverride

import cats.data.NonEmptyList
import cats.data.Validated._
import cats.implicits._
import geotrellis.raster.{MultibandTile, Tile}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.render._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import java.util.UUID


// --- //

package object ast {

  /** Perform a binary operation on RDDs, while preserving any metadata they had. */
  private def binary(
    f: (RDD[(SpatialKey, Tile)], RDD[(SpatialKey, Tile)]) => RDD[(SpatialKey, Tile)],
    tlr0: TileLayerRDD[SpatialKey],
    tlr1: TileLayerRDD[SpatialKey]
  ): TileLayerRDD[SpatialKey] = {
    TileLayerRDD(f(tlr0, tlr1), tlr0.metadata.combine(tlr1.metadata))
  }

  /** Perform a binary reduction on the arguments of some Operation node.
    * This is complicated by [[Constant]], which renders certain AST
    * combinations meaningless when dealing with RDDs and GeoTrellis layers. For
    * instance, how should one evaluate the AST:
    * {{{
    * Masking <- Constant
    * }}}
    * While this may have meaning on the Tile server, it is meaningless here,
    * since there is no way to devise a key-space nor metadata for the RDD we'd
    * have to pull from thin air.
    *
    * This function _looks_ like it can throw an error, but it never should.
    * ASTs that contain Constant nodes in illegal positions will be rejected by
    * the validator before this code here can ever run.
    */
  private def reduce(
    f: (Double, Double) => Double,
    g: (Double, RDD[(SpatialKey, Tile)]) => RDD[(SpatialKey, Tile)],
    h: (RDD[(SpatialKey, Tile)], Double) => RDD[(SpatialKey, Tile)],
    i: (RDD[(SpatialKey, Tile)], RDD[(SpatialKey, Tile)]) => RDD[(SpatialKey, Tile)],
    asts: List[MapAlgebraAST],
    rdds: Map[UUID, TileLayerRDD[SpatialKey]]
  ): Either[Double, TileLayerRDD[SpatialKey]] = {
    asts.map({
      case Constant(_, c, _) => Left(c)
      case ast => eval(ast, rdds)
    }).reduceLeft[Either[Double, TileLayerRDD[SpatialKey]]]({
      case (Left(c1), Left(c2))       => Left(f(c1, c2))
      case (Left(c), Right(rdd))      => Right(rdd.withContext(g(c, _)))
      case (Right(rdd), Left(c))      => Right(rdd.withContext(h(_, c)))
      case (Right(rdd1), Right(rdd2)) => Right(binary(i, rdd1, rdd2))
    })
  }

  /* Perform Map Algebra over a validated RDD-filled AST */
  @SuppressWarnings(Array("TraversableHead"))
  private def eval(
    ast: MapAlgebraAST,
    rdds: Map[UUID, TileLayerRDD[SpatialKey]]
  ): Either[Double, TileLayerRDD[SpatialKey]] = ast match {
    /* --- LEAVES --- */
    case Source(id, _) => Right(rdds(id))
    case Constant(id, const, _) =>
      sys.error("Export: If you're seeing this, there is an error in the AST validation logic.")
    case ToolReference(_, _) =>
      sys.error("Export: If you're seeing this, there is an error in the AST validation logic.")

    /* --- BINARY OPERATIONS --- */
    case Addition(args, _, _) => reduce({_ + _}, {_ +: _}, {_ + _}, {_ + _}, args, rdds)
    case Subtraction(args, _, _) => reduce({_ - _}, {_ -: _}, {_ - _}, {_ - _}, args, rdds)
    case Multiplication(args, _, _) => reduce({_ * _}, {_ *: _}, {_ * _}, {_ * _}, args, rdds)
    case Division(args, _, _) => reduce({_ / _}, {_ /: _}, {_ / _}, {_ / _}, args, rdds)
    case Max(args, _, _) =>
      reduce({_.max(_)}, { (c, rdd) => rdd.localMax(c) }, {_.localMax(_)}, {_.localMax(_)}, args, rdds)
    case Min(args, _, _) =>
      reduce({_.min(_)}, { (c, rdd) => rdd.localMin(c) }, {_.localMin(_)}, {_.localMin(_)}, args, rdds)

    /* --- UNARY OPERATIONS --- */
    /* The `head` calls here will never fail, nor will they produce a `Constant` */
    case Classification(args, _, _, classMap) =>
      eval(args.head, rdds).map(_.withContext(_.color(classMap.toColorMap)))
    case Masking(args, _, _, mask) => eval(args.head, rdds).map(_.mask(mask))

    /* --- FOCAL OPERATIONS --- */
    /* The `head` calls here will never fail, nor will they produce a `Constant` */
    case FocalMax(args, _, _, neighborhood) => eval(args.head, rdds).map(_.focalMax(neighborhood))
    case FocalMin(args, _, _, neighborhood) => eval(args.head, rdds).map(_.focalMin(neighborhood))
    case FocalMean(args, _, _, neighborhood) => eval(args.head, rdds).map(_.focalMean(neighborhood))
    case FocalMedian(args, _, _, neighborhood) => eval(args.head, rdds).map(_.focalMedian(neighborhood))
    case FocalMode(args, _, _, neighborhood) => eval(args.head, rdds).map(_.focalMode(neighborhood))
    case FocalSum(args, _, _, neighborhood) => eval(args.head, rdds).map(_.focalSum(neighborhood))
    case FocalStdDev(args, _, _, neighborhood) => eval(args.head, rdds).map(_.focalStandardDeviation(neighborhood))
  }

  /** Evaluate an AST of RDD Sources. Assumes that the AST's
    * [[NodeMetadata]] has already been replaced, if applicable.
    */
  def interpretRDD(
    ast: MapAlgebraAST,
    sourceMapping: Map[UUID, RFMLRaster],
    overrides: Map[UUID, ParamOverride],
    zoom: Int,
    sceneLocs: Map[UUID, String],
    projLocs: Map[UUID, List[(UUID, String)]]
  )(implicit sc: SparkContext): Interpreted[TileLayerRDD[SpatialKey]] = {

    /* Guarantee correctness before performing Map Algebra */
    val pure = Interpreter.interpretPure[Unit](ast, sourceMapping)
    val over = Interpreter.overrideParams(ast, overrides)
    val rdds = sourceMapping.mapValues(r => fetch(r, zoom, sceneLocs, projLocs)).sequence

    (pure |@| over |@| rdds).map({ case (_, tree, rs) => eval(tree, rs) match {
      case Left(_) => sys.error("Export: If you're seeing this, there is an error in the AST validation logic.")
      case Right(rdd) => rdd
    }})
  }

  /** This requires that for each [[RFMLRaster]] that a band number be specified. */
  private def fetch(
    raster: RFMLRaster,
    zoom: Int,
    sceneLocs: Map[UUID, String],
    projLocs: Map[UUID, List[(UUID, String)]]
  )(implicit sc: SparkContext): Interpreted[TileLayerRDD[SpatialKey]] = raster match {

    case ProjectRaster(id, None, _) => Invalid(NonEmptyList.of(NoBandGiven(id)))
    case ProjectRaster(id, Some(band), maybeND) => getStores(id, projLocs) match {
      case None => Invalid(NonEmptyList.of(AttributeStoreFetchError(id)))
      case Some(stores) => {
        val rdds: List[TileLayerRDD[SpatialKey]] =
          stores.map({ case (sceneId, store) =>
            S3LayerReader(store)
              .read[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](LayerId(sceneId.toString, zoom))
              .withContext({rdd =>
                rdd.mapValues({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              })
          })

        Valid(rdds.reduce(_ merge _))
      }
    }
    case SceneRaster(id, None, _) => Invalid(NonEmptyList.of(NoBandGiven(id)))
    case SceneRaster(id, Some(band), maybeND) => getStore(id, sceneLocs) match {
      case None => Invalid(NonEmptyList.of(AttributeStoreFetchError(id)))
      case Some(store) => {
        val rdd = S3LayerReader(store)
          .read[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](LayerId(id.toString, zoom))
          .withContext({ rdd =>
            rdd.mapValues({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
          })

        Valid(rdd)
      }
    }
  }

  /** Cleanly fetch an `AttributeStore`, given some the ID of a Scene (which
    * represents a Layer).
    */
  private def getStore(layer: UUID, sceneLocs: Map[UUID, String]): Option[AttributeStore] = for {
    ingestLocation <- sceneLocs.get(layer)
    result <- S3InputFormat.S3UrlRx.findFirstMatchIn(ingestLocation)
  } yield {
    S3AttributeStore(result.group("bucket"), result.group("prefix"))
  }

  /** Try to get an [[AttributeStore]] for each Scene in the given Project. */
  private def getStores(
    proj: UUID,
    projLocs: Map[UUID, List[(UUID, String)]]
  ): Option[List[(UUID, S3AttributeStore)]] = for {
    (ids, locs) <- projLocs.get(proj).map(_.unzip)
    results <- locs.map(S3InputFormat.S3UrlRx.findFirstMatchIn(_)).sequence
  } yield {
    val stores = results.map(r => S3AttributeStore(r.group("bucket"), r.group("prefix")))

    ids.zip(stores)
  }
}
