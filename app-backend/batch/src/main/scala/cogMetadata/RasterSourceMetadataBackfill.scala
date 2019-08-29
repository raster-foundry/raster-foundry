package com.rasterfoundry.batch.cogMetadata

import java.net.URLDecoder
import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.rasterfoundry.batch.Job
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database.RasterSourceMetadataDao
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel.RasterSourceMetadata
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.contrib.vlm.gdal.GDALRasterSource

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

  // presence of the ingest location is guaranteed by the filter in the sql string
  @SuppressWarnings(Array("OptionGet"))
  def insertRasterSourceMetadata(cogTuple: CogTuple)(
      implicit xa: Transactor[IO]): IO[RasterSourceMetadata] = {
    logger.info(s"Getting Raster Source: ${cogTuple._1}")
    val rasterSource = GDALRasterSource(URLDecoder.decode(cogTuple._2, "UTF-8"))
    logger.info(s"Getting Metadata: ${cogTuple._1}")
    val rasterSourceMetadata = RasterSourceMetadata(
      rasterSource.dataPath,
      rasterSource.crs,
      rasterSource.bandCount,
      rasterSource.cellType,
      rasterSource.noDataValue,
      rasterSource.gridExtent,
      rasterSource.resolutions
    )
    logger.info(s"Inserting Metadata: ${cogTuple._1}")
    val md = RasterSourceMetadataDao
      .update(cogTuple._1, rasterSourceMetadata)
      .transact(xa)
      .map(_ => rasterSourceMetadata)
    println(s"Done: ${cogTuple._1}")
    md
  }

  def runJob(args: List[String]) =
    RFTransactor.xaResource.use { transactor =>
      implicit val xa = transactor
      for {
        scenes <- getScenesToBackfill(xa)
        metadata <- scenes.map(t => insertRasterSourceMetadata(t)).parSequence
      } yield {
        metadata
          .foreach(rsm => println(s"Inserted metadata for ${rsm.dataPath}"))
      }
    }
}
