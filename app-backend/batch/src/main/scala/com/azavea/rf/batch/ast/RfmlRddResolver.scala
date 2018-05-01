package com.azavea.rf.batch.ast

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.maml._

import com.azavea.maml.ast._
import com.azavea.maml.spark.ast._
import com.azavea.maml.eval._
import com.azavea.maml.eval.tile._
import com.azavea.maml.util.NeighborhoodConversion
import cats._
import cats.data.Validated._
import cats.data.{NonEmptyList => NEL, _}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.tiling._
import geotrellis.vector.{Extent, MultiPolygon}
import geotrellis.slick.Projected
import geotrellis.spark.io.postgres.PostgresAttributeStore
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.util.{Try, Failure, Success}
import java.util.UUID


/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
object RfmlRddResolver extends LazyLogging {

  val intNdTile = IntConstantTile(NODATA, 256, 256)

  def resolveRdd(
    fullExp: Expression,
    zoom: Int,
    sceneLocs: Map[UUID,String],
    projLocs: Map[UUID, List[(UUID, String)]]
  )(implicit sc: SparkContext): Interpreted[Expression] = {

    def eval(exp: Expression): Interpreted[Expression] =
      exp match {
        case pr@ProjectRaster(projId, None, celltype) =>
          Invalid(NEL.of(NonEvaluableNode(exp, Some("no band given"))))
        case pr@ProjectRaster(projId, Some(band), celltype) =>
          getStores(projId, projLocs) match {
            case None => Invalid(NEL.of(UnknownTileResolutionError(pr, None)))
            case Some(stores) =>
              val rdds: List[TileLayerRDD[SpatialKey]] =
                stores.map({ case (sceneId, store) =>
                  S3LayerReader(store)
                    .read[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](LayerId(sceneId.toString, zoom))
                    .withContext({rdd =>
                      rdd.mapValues({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                    })
                })
              Valid(RDDLiteral(rdds.reduce(_ merge _)))
          }
        case sr@SceneRaster(sceneId, None, celltype) =>
          Invalid(NEL.of(NonEvaluableNode(exp, Some("no band given"))))
        case sr@SceneRaster(sceneId, Some(band), celltype) =>
          getStore(sceneId, sceneLocs) match {
            case None =>
              Invalid(NEL.of(NonEvaluableNode(sr, Some("attribute store error"))))
            case Some(store) =>
              val rdd = S3LayerReader(store)
                .read[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](LayerId(sceneId.toString, zoom))
                .withContext({ rdd =>
                  rdd.mapValues({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                })

            Valid(RDDLiteral(rdd))
          }
        case _ =>
          exp.children
            .map({ child => eval(child) })
            .toList.sequence
            .map({ exp.withChildren(_) })
      }
    eval(fullExp)
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

