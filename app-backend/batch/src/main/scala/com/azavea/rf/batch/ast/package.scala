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
import com.azavea.rf.tool.eval.{AttributeStoreFetchError, Interpreter, UnhandledCase}
import com.azavea.rf.tool.eval.Interpreter.Interpreted
import geotrellis.raster.Tile
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.render._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

// --- //

package object ast {

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
  ): Interpreted[RDD[(SpatialKey, Tile)]] = {

    /* Perform Map Algebra over a validated RDD-filled AST */
    def eval(
      ast: MapAlgebraAST,
      rdds: Map[UUID, RDD[(SpatialKey, Tile)]]
    ): RDD[(SpatialKey, Tile)] = ast match {
      case Source(id, _) => rdds(id)
      case Addition(args, _, _) => args.map(eval(_, rdds)).reduce(_ + _)
      case Subtraction(args, _, _) => args.map(eval(_, rdds)).reduce(_ - _)
      case Multiplication(args, _, _) => args.map(eval(_, rdds)).reduce(_ * _)
      case Division(args, _, _) => args.map(eval(_, rdds)).reduce(_ / _)
      case Classification(args, _, _, classMap) => eval(args.head, rdds).color(classMap.toColorMap)
      case _ => ???
    }

    /* Guarantee correctness before performing Map Algebra */
    val pure = Interpreter.interpretPure[Unit](ast, sourceMapping)
    val rdds = sourceMapping.mapValues(r => fetch(r, zoom)).sequence

    (pure |@| rdds).map({ case (_, rs) => eval(ast, rs) })
  }

  def fetch(raster: RFMLRaster, zoom: Int)
    (implicit ec: ExecutionContext,
      database: Database,
      sc: SparkContext
    ): Interpreted[RDD[(SpatialKey, Tile)] with Metadata[TileLayerMetadata[SpatialKey]]] = raster match {

    case ProjectRaster(id, _) => Invalid(NonEmptyList.of(UnhandledCase(id)))

    case SceneRaster(id, _) => {
      val storeF: Future[Option[AttributeStore]] = getStore(id).recover({ case _ => None })

      Await.result(storeF, 10 seconds) match {
        case None => Invalid(NonEmptyList.of(AttributeStoreFetchError(id)))
        case Some(store) => {
          val rdd = S3LayerReader(store)
            .read[SpatialKey, Tile, TileLayerMetadata[SpatialKey]](LayerId(id.toString, zoom))

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
