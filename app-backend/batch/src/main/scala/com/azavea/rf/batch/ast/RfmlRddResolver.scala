package com.azavea.rf.batch.ast

import com.azavea.rf.common.utils.CogUtils
import com.azavea.rf.datamodel._
import com.azavea.rf.database.SceneToProjectDao
import com.azavea.rf.database.util.RFTransactor
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
import cats.effect.IO
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.Transactor
import doobie.implicits._
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.s3.util._
import geotrellis.spark.tiling._
import geotrellis.vector.{Extent, MultiPolygon}
import geotrellis.spark.io.postgres.PostgresAttributeStore
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.concurrent.Future
import scala.util.{Try, Failure, Success}
import java.util.UUID


/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
object RfmlRddResolver extends LazyLogging {

  implicit val xa = RFTransactor.xa

  val intNdTile = IntConstantTile(NODATA, 256, 256)

  def resolveRdd(
    fullExp: Expression,
    zoom: Int,
    sceneLocs: Map[UUID,String],
    projLocs: Map[UUID, List[(UUID, String)]]
  )(implicit sc: SparkContext, xa: Transactor[IO]): IO[Interpreted[Expression]] = {
    // TODO:
    // - get mosaic definition for all the projects in projLocs -- those have scene types
    // - that ends up UUID -> List[Source] (ish)
    // - eval against this map
    // - in each list of sources, map to discriminate over scene type

    val sourceFs: IO[List[(Option[CellType], Int) => Source]] = projLocs.keysIterator.toList.traverse(
      (k: UUID) => SceneToProjectDao.getMosaicDefinition(k).transact(xa)
    ).map(_.flatten).map( (mosaicDefinitions: List[MosaicDefinition]) =>
      mosaicDefinitions map {
        case MosaicDefinition(sceneId, _, Some(SceneType.COG), Some(s)) =>
          (cellTypeO: Option[CellType], band: Int) => CogRaster(sceneId, Some(band), cellTypeO, s)
        case MosaicDefinition(sceneId, _, Some(SceneType.Avro), Some(s)) =>
          (cellTypeO: Option[CellType], band: Int) => SceneRaster(sceneId, Some(band), cellTypeO, s)
      }
    )

    def eval(exp: Expression): IO[Interpreted[Expression]] =
      exp match {
        case pr@ProjectRaster(projId, None, celltype) =>
          IO.pure(Invalid(NEL.of(NonEvaluableNode(exp, Some("no band given")))))
        case pr@ProjectRaster(projId, Some(band), celltypeO) => sourceFs map {
          (funcs: List[(Option[CellType], Int) => Source]) => {
            val sources = funcs map ( _(celltypeO, band) )
            val rddsAndErrors = sources map {
              case sr@SceneRaster(_, _, _, _) => {
                avroSceneSourceAsRDD(sr, zoom)
              }
              case cr@CogRaster(_, _, _, _) => {
                cogSceneSourceAsRDD(cr)
              }
            }

            val errors = rddsAndErrors flatMap {
              case Left(nel) => Some(nel)
              case _ => None
            }

            if (errors.length > 0) {
              // ::: is the nonempty list combiner
              Invalid(errors.reduce (_ ::: _))
            } else {
              Valid(RDDLiteral(rddsAndErrors.flatMap(_.toOption) reduce { _ merge _ }))
            }
          }
        }
        case _ =>
          IO(
            throw new IllegalArgumentException("Only project sources can be resolved")
          )
      }
    eval(fullExp)
  }

    private def cogSceneSourceAsRDD(source: CogRaster)(implicit sc: SparkContext): Either[NEL[NonEvaluableNode], TileLayerRDD[SpatialKey]] = {
      val tileRdd = CogUtils.fromUriAsRdd(source.location)
        .withContext( { rdd =>
          rdd.mapValues({ mbtile => mbtile.band(source.band.get).interpretAs(source.celltype.getOrElse(mbtile.cellType)) })
      })
      Right(tileRdd)
    }

  private def avroSceneSourceAsRDD(source: SceneRaster, zoom: Int)(implicit sc: SparkContext): Either[NEL[NonEvaluableNode], TileLayerRDD[SpatialKey]] = {
    val storeO = S3InputFormat.S3UrlRx.findFirstMatchIn(source.location) map {
      regexResult => {
        S3AttributeStore(regexResult.group("bucket"), regexResult.group("prefix"))
      }
    }
    storeO match {
      case None =>
        Left(NEL.of(NonEvaluableNode(source, Some("attribute store error"))))
      case Some(store) =>
        val rdd = S3LayerReader(store)
          .read[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](LayerId(source.sceneId.toString, zoom))
          .withContext({ rdd =>
            rdd.mapValues({ tile => tile.band(source.band.get).interpretAs(source.celltype.getOrElse(tile.cellType)) })
          })
        Right(rdd)
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
