package com.azavea.rf.batch

import java.util.UUID

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

import cats.data.NonEmptyList
import cats.data.Validated._
import cats.implicits._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Scenes
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.eval.Interpreter.Interpreted
import geotrellis.raster.{MultibandTile, Tile}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.render._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

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
    zoom: Int
  )(implicit ec: ExecutionContext,
    database: Database,
    sc: SparkContext
  ): Interpreted[TileLayerRDD[SpatialKey]] = {

    /* Perform Map Algebra over a validated RDD-filled AST */
    def eval(
      ast: MapAlgebraAST,
      rdds: Map[UUID, TileLayerRDD[SpatialKey]]
    ): TileLayerRDD[SpatialKey] = ast match {
      case Source(id, _) => rdds(id)
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
      case _ => ???
    }

    /* Guarantee correctness before performing Map Algebra */
    val pure = Interpreter.interpretPure[Unit](ast, sourceMapping)
    val rdds = sourceMapping.mapValues(r => fetch(r, zoom)).sequence

    (pure |@| rdds).map({ case (_, rs) => eval(ast, rs) })
  }

  /** This requires that for each [[RFMLRaster]] that a band number be specified. */
  def fetch(raster: RFMLRaster, zoom: Int)
    (implicit ec: ExecutionContext,
      database: Database,
      sc: SparkContext
    ): Interpreted[TileLayerRDD[SpatialKey]] = raster match {

    case ProjectRaster(id, _) => Invalid(NonEmptyList.of(UnhandledCase(id)))
    case SceneRaster(id, None) => Invalid(NonEmptyList.of(NoBandGiven(id)))
    case SceneRaster(id, Some(band)) => {
      val storeF: Future[Option[AttributeStore]] = getStore(id).recover({ case _ => None })

      Await.result(storeF, 10 seconds) match {
        case None => Invalid(NonEmptyList.of(AttributeStoreFetchError(id)))
        case Some(store) => {
          val rdd = S3LayerReader(store)
            .read[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](LayerId(id.toString, zoom))
            .withContext(rdd => rdd.mapValues(_.band(band)))

          Valid(rdd)
        }
      }
    }
  }

  /** Cleanly fetch an `AttributeStore`, given some the ID of a Scene (which
    * represents a Layer).
    */
  def getStore(layer: UUID)
    (implicit ec: ExecutionContext, database: Database): Future[Option[AttributeStore]] = {

    Scenes.getSceneForCaching(layer).map({ maybeScene =>
      for {
        scene <- maybeScene
        catalogUri <- scene.ingestLocation
        result <- S3InputFormat.S3UrlRx.findFirstMatchIn(catalogUri)
      } yield {
        S3AttributeStore(result.group("bucket"), result.group("prefix"))
      }
    })
  }
}
