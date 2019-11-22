package com.rasterfoundry.batch.cogMetadata

import java.util.UUID
import java.util.concurrent.Executors

import cats.effect._
import cats.implicits._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.rasterfoundry.backsplash.BacksplashGeotiffReader
import com.rasterfoundry.batch.Job
import com.rasterfoundry.common.{BacksplashGeoTiffInfo, RollbarNotifier}
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{RasterSourceMetadataDao, SceneDao}
import com.rasterfoundry.datamodel.RasterSourceMetadata
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.contrib.vlm.gdal.GDALDataPath

import scala.concurrent.ExecutionContext

object RasterSourceMetadataBackfill extends Job with RollbarNotifier {

  type CogTuple = (UUID, String)

  val name = "rastersource-metadata-backfill"

  def getScenesToBackfill(
      implicit xa: Transactor[IO]): IO[List[(UUID, String)]] = {
    logger.info("Finding COG scenes without metadata in layer_attributes")
    fr"""select
           id, ingest_location
         from
           scenes
         where
           scene_type = 'COG' and
           ingest_location is not null and
           crs is null;
       """.query[(UUID, String)].to[List].transact(xa) map { tuples =>
      {
        logger.info(s"Found ${tuples.length} scenes to get metadata for")
        tuples
      }
    }
  }

  def getBacksplashInfo(
      id: UUID,
      path: String): IO[Either[Throwable, BacksplashGeoTiffInfo]] = {
    IO(BacksplashGeotiffReader.getBacksplashGeotiffInfo(path)).attempt map {
      case l @ Left(_) =>
        logger.error(
          s"""Scene(id='$id'): Failed to fetch geotiff for path "$path".""")
        l
      case r => r
    }
  }

  // presence of the ingest location is guaranteed by the filter in the sql string
  @SuppressWarnings(Array("OptionGet"))
  def insertRasterSourceMetadata(id: UUID,
                                 path: String,
                                 backsplashInfo: BacksplashGeoTiffInfo)(
      implicit xa: Transactor[IO]): IO[Int] = {
    val rasterSourceMetadata = RasterSourceMetadata(
      GDALDataPath(path),
      backsplashInfo.crs,
      backsplashInfo.bandCount,
      backsplashInfo.tiffTags.cellType,
      backsplashInfo.noDataValue,
      backsplashInfo.tiffTags.rasterExtent.toGridType[Long],
      backsplashInfo.overviews
        .map(_.tiffTags.rasterExtent.toGridType[Long])
        .toList
    )
    RasterSourceMetadataDao
      .update(id, rasterSourceMetadata)
      .transact(xa)
  }

  def insertGeotiffInfo(id: UUID, backsplashInfo: BacksplashGeoTiffInfo)(
      implicit xa: Transactor[IO]): IO[Int] = {
    logger.info(s"Getting Metadata: ${id}")
    SceneDao.updateSceneGeoTiffInfo(backsplashInfo, id).transact(xa)
  }

  def runJob(args: List[String]) = {

    RFTransactor.xaResource.use { transactor =>
      implicit val xa = transactor
      val scenesToUpdate = getScenesToBackfill(xa).unsafeRunSync()

      val rasterIO: ContextShift[IO] = IO.contextShift(
        ExecutionContext.fromExecutor(
          Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("backfill-%d").build()
          )
        ))

      implicit val contextShift: ContextShift[IO] = rasterIO

      val metadataIOs = scenesToUpdate.parTraverse {
        case (id, uri) =>
          for {
            backsplashInfo <- getBacksplashInfo(id, uri)
            rmdResult <- backsplashInfo.traverse(
              insertRasterSourceMetadata(id, uri, _))
            geotiffResult <- backsplashInfo.traverse(insertGeotiffInfo(id, _))
          } yield (rmdResult, geotiffResult)
      }

      metadataIOs
        .map(metadataInserts => {
          val (lefts, rights) =
            metadataInserts partition {
              case (Right(_), Right(_)) => false
              case _                    => true
            }
          if (lefts.length > 0) {
            logger.error(
              s"Failed to insert metadata for ${lefts.length} resources")
          }
          logger.info(s"Inserted metadata for ${rights.length} resources")
        })
    }
  }
}
