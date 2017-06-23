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

    /* Perform Map Algebra over a validated RDD-filled AST */
    @SuppressWarnings(Array("TraversableHead"))
    def eval(
      ast: MapAlgebraAST,
      rdds: Map[UUID, TileLayerRDD[SpatialKey]]
    ): TileLayerRDD[SpatialKey] = ast match {
      /* --- LEAVES --- */
      case Source(id, _) => rdds(id)
      case Constant(id, const, _) => ???

      /* --- OPERATIONS --- */
      case Addition(args, _, _) =>
        args.map(eval(_, rdds)).reduce((acc,r) => binary({_ + _}, acc, r))
      case Subtraction(args, _, _) =>
        args.map(eval(_, rdds)).reduce((acc,r) => binary({_ - _}, acc, r))
      case Multiplication(args, _, _) =>
        args.map(eval(_, rdds)).reduce((acc,r) => binary({_ * _}, acc, r))
      case Division(args, _, _) =>
        args.map(eval(_, rdds)).reduce((acc,r) => binary({_ / _}, acc, r))
      case Classification(args, _, _, classMap) =>
        eval(args.head, rdds).withContext(_.color(classMap.toColorMap))
      case Max(args, _, _) => {
        val kids: List[TileLayerRDD[SpatialKey]] = args.map(eval(_, rdds))

        /* The head call will never fail */
        ContextRDD(kids.head.localMax(kids.tail), kids.map(_.metadata).reduce(_ combine _))
      }
      case Min(args, _, _) => {
        val kids: List[TileLayerRDD[SpatialKey]] = args.map(eval(_, rdds))

        /* The head call will never fail */
        ContextRDD(kids.head.localMin(kids.tail), kids.map(_.metadata).reduce(_ combine _))
      }
      case Masking(args, _, _, mask) => eval(args.head, rdds).mask(mask)
    }

    /* Guarantee correctness before performing Map Algebra */
    val pure = Interpreter.interpretPure[Unit](ast, sourceMapping)
    val over = Interpreter.overrideParams(ast, overrides)
    val rdds = sourceMapping.mapValues(r => fetch(r, zoom, sceneLocs, projLocs)).sequence

    (pure |@| over |@| rdds).map({ case (_, tree, rs) => eval(tree, rs) })
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
