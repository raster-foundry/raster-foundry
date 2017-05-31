package com.azavea.rf.batch.landsat8.airflow

import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.tables._
import com.azavea.rf.datamodel._

import io.circe._
import io.circe.syntax._
import geotrellis.proj4.CRS
import geotrellis.slick.Projected
import geotrellis.vector._
import jp.ne.opt.chronoscala.Imports._
import org.postgresql.util.PSQLException

import java.time.{LocalDate, ZoneOffset}
import java.util.UUID

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.control.Breaks._

@deprecated(message = "Use com.azavea.rf.batch.landsat8.airflow.ImportLandsat8C1 instead, as USGS changes LC8 storage rules: https://landsat.usgs.gov/landsat-collections", since = "https://github.com/azavea/raster-foundry/pull/1821")
case class ImportLandsat8(startDate: LocalDate = LocalDate.now(ZoneOffset.UTC), threshold: Int = 10)(implicit val database: DB) extends Job {
  val name = ImportLandsat8.name

  /** Get S3 client per each call */
  def s3Client = S3(region = landsat8Config.awsRegion)

  protected def scenesFromCsv(srcProj: CRS = CRS.fromName("EPSG:4326"), targetProj: CRS = CRS.fromName("EPSG:3857")): Future[ListBuffer[Scene.Create]] = {
    val reader = CSV.parse(landsat8Config.usgsLandsatUrl)
    val iterator = reader.iterator()

    val endDate = startDate + 1.day
    val buffer = ListBuffer[Future[Option[Scene.Create]]]()
    val (_, indexToId) = CSV.getBiFunctions(iterator.next())

    var counter = 0
    breakable {
      while(iterator.hasNext) {
        val line = reader.readNext()

        val row =
          line
            .zipWithIndex
            .map { case (v, i) => indexToId(i) -> v }
            .toMap

        row.get("acquisitionDate") foreach { dateStr =>
          val date = LocalDate.parse(dateStr)
          if (startDate <= date && endDate > date) buffer += csvRowToScene(row, srcProj, targetProj)
          else if (date < startDate) counter += 1
        }

        if (counter > threshold) break
      }
    }

    Future.sequence(buffer).map(_.flatten)
  }

  protected def getLandsatPath(landsatId: String): String = {
    val (wPath, wRow) = landsatId.substring(3, 6) -> landsatId.substring(6, 9)
    val path = s"L8/$wPath/$wRow/$landsatId"
    logger.debug(s"Constructed path: $path")
    path
  }

  protected def getLandsatUrl(landsatId: String): String = {
    val rootUrl = s"https://landsat-pds.s3.amazonaws.com/${getLandsatPath(landsatId)}"
    logger.debug(s"Constructed Root URL: $rootUrl")
    rootUrl
  }

  protected def sizeFromPath(tifPath: String, landsatId: String): Int = {
    val path = s"${getLandsatPath(landsatId)}/$tifPath"
    logger.info(s"Getting object size for path: $path")
    s3Client.getObject(landsat8Config.bucketName, path).getObjectMetadata.getContentLength.toInt
  }

  protected def createThumbnails(sceneId: UUID, landsatId: String): List[Thumbnail.Identified] = {
    val path = getLandsatUrl(landsatId)
    val smallUrl = s"$path${landsatId}_thumb_small.jpg}"
    val largeUrl = s"$path${landsatId}_thumb_large.jpg}"

    Thumbnail.Identified(
      id = None,
      organizationId = UUID.fromString(landsat8Config.organization),
      thumbnailSize = ThumbnailSize.Small,
      widthPx = 228,
      heightPx = 233,
      sceneId = sceneId,
      url = smallUrl
    ) :: Thumbnail.Identified(
      id = None,
      organizationId = UUID.fromString(landsat8Config.organization),
      thumbnailSize = ThumbnailSize.Large,
      widthPx = 1143,
      heightPx = 1168,
      sceneId = sceneId,
      url = largeUrl
    ) :: Nil
  }


  @SuppressWarnings(Array("TraversableHead"))
  protected def csvRowToScene(row: Map[String, String], srcProj: CRS = CRS.fromName("EPSG:4326"), targetProj: CRS = CRS.fromName("EPSG:3857")): Future[Option[Scene.Create]] = Future {
    val sceneId = UUID.randomUUID()
    val landsatId = row("sceneID")

    val ll = row("lowerLeftCornerLongitude").toDouble -> row("lowerLeftCornerLatitude").toDouble
    val lr = row("lowerRightCornerLongitude").toDouble -> row("lowerRightCornerLatitude").toDouble
    val ul = row("upperLeftCornerLongitude").toDouble -> row("upperLeftCornerLatitude").toDouble
    val ur = row("upperRightCornerLongitude").toDouble -> row("upperRightCornerLatitude").toDouble

    val srcCoords  = ll :: lr :: ul :: ur :: Nil
    val srcPolygon = Polygon(Line(srcCoords :+ srcCoords.head))

    val sortedByX = srcCoords.sortBy(_.x)
    val sortedByY = srcCoords.sortBy(_.y)

    val extent = Extent(
      xmin = sortedByX.head.x,
      ymin = sortedByY.head.y,
      xmax = sortedByX.last.x,
      ymax = sortedByY.last.y
    )

    val (transformedCoords, transformedExtent) = {
      if (srcProj.equals(targetProj)) srcPolygon -> extent
      else srcPolygon.reproject(srcProj, targetProj) -> extent.reproject(srcProj, targetProj)
    }

    val landsatPath = getLandsatPath(landsatId)
    val s3Url = s"${landsat8Config.awsLandsatBase}${landsatPath}/index.html"

    if (!isUriExists(s3Url)) {
      logger.warn(
        "AWS and USGS are not always in sync. Try again in several hours.\n" +
          s"If you believe this message is in error, check $s3Url manually."
      )
      None
    } else {
      val acquisitionDate =
        row.get("acquisitionDate").map { dt =>
          new java.sql.Timestamp(
            LocalDate
              .parse(dt)
              .atStartOfDay(ZoneOffset.UTC)
              .toInstant
              .getEpochSecond * 1000l
          )
        }

      val cloudCover = row.get("cloudCoverFull").map(_.toFloat)
      val sunElevation = row.get("sunElevation").map(_.toFloat)
      val sunAzimuth = row.get("sunAzimuth").map(_.toFloat)
      val bands15m = landsat8Config.bandLookup.`15m`
      val bands30m = landsat8Config.bandLookup.`30m`
      val tags = List("Landsat 8", "GeoTIFF")
      val sceneMetadata = row.filter { case (_, v) => v.nonEmpty }

      val images =
        (bands15m.map { band =>
          val b = band.name.split(" - ").last
          (15f, s"${landsatId}_B${b}.TIF", band)
        } ++ bands30m.map { band =>
          val b = band.name.split(" - ").last
          (30f, s"${landsatId}_B${b}.TIF", band)
        }).map { case (resolution, tiffPath, band) =>
          Image.Banded(
            organizationId = landsat8Config.organizationUUID,
            rawDataBytes = sizeFromPath(tiffPath, landsatId),
            visibility = Visibility.Public,
            filename = tiffPath,
            sourceUri = s"${getLandsatUrl(landsatId)}/${tiffPath}",
            owner = Some(airflowUser),
            scene = sceneId,
            imageMetadata = Json.Null,
            resolutionMeters = resolution,
            metadataFiles = List(),
            bands = List(band)
          )
        }

      Some(Scene.Create(
        id = Some(sceneId),
        organizationId = landsat8Config.organizationUUID,
        ingestSizeBytes = 0,
        visibility = Visibility.Public,
        tags = tags,
        datasource = landsat8Config.datasourceUUID,
        sceneMetadata = sceneMetadata.asJson,
        name = s"L8 $landsatPath",
        owner = Some(airflowUser),
        tileFootprint = targetProj.epsgCode.map(Projected(MultiPolygon(transformedExtent.toPolygon()), _)),
        dataFootprint = targetProj.epsgCode.map(Projected(MultiPolygon(transformedCoords), _)),
        metadataFiles = List(s"${landsat8Config.awsLandsatBase}${landsatPath}/${landsatId}_MTL.txt"),
        images = images,
        thumbnails = createThumbnails(sceneId, landsatId),
        ingestLocation = None,
        filterFields = SceneFilterFields(
          cloudCover = cloudCover,
          sunAzimuth = sunAzimuth,
          sunElevation = sunElevation,
          acquisitionDate = acquisitionDate
        ),
        statusFields = SceneStatusFields(
          thumbnailStatus = JobStatus.Success,
          boundaryStatus = JobStatus.Success,
          ingestStatus = IngestStatus.NotIngested
        )
      ))

    }
  }

  def run: Unit = {
    logger.info("Importing scenes...")
    Users.getUserById(airflowUser).flatMap { userOpt =>
      val user = userOpt.getOrElse {
        val e = new Exception(s"User $airflowUser doesn't exist.")
        sendError(e)
        throw e
      }

      scenesFromCsv()
        .flatMap { scenes =>
          Future.sequence(scenes.map { scene =>
            val id = scene.id.map(_.toString).getOrElse("")
            logger.info(s"Importing scene $id...")
            val future =
              Scenes.insertScene(scene, user).map(_.toScene).recover {
                case e: PSQLException => {
                  logger.error(s"An error occurred during scene $id import. Skipping...")
                  logger.error(e.stackTraceString)
                  sendError(e)
                  scene.toScene(user)
                }
              }

            future onComplete {
              case Success(s) => logger.info(s"Finished importing scene ${s.id}.")
              case Failure(e) => {
                logger.error(s"An error occurred during scene $id import.")
                logger.error(e.stackTraceString)
                sendError(e)
              }
            }
            future
          })
        }
    } onComplete {
      case Success(scenes) => {
        if(scenes.nonEmpty) logger.info(s"Successfully imported scenes: ${scenes.map(_.id.toString).mkString(", ")}.")
        else {
          val e = new Exception(s"No scenes available for the ${startDate}")
          e.printStackTrace()
          sendError(e)
          stop
          sys.exit(1)
        }
        stop
      }
      case Failure(e) => {
        e.printStackTrace()
        sendError(e)
        stop
        sys.exit(1)
      }
    }
  }
}

object ImportLandsat8 {
  val name = "import_landsat8"

  def main(args: Array[String]): Unit = {
    implicit val db = DB.DEFAULT

    /** Since 30/04/2017 old LC8 collection is not updated */
    val job = args.toList match {
      case List(date, threshold) if LocalDate.parse(date) > LocalDate.of(2017, 4, 30) => ImportLandsat8C1(LocalDate.parse(date), threshold.toInt)
      case List(date) if LocalDate.parse(date) > LocalDate.of(2017, 4, 30) => ImportLandsat8C1(LocalDate.parse(date))
      case List(date, threshold) => ImportLandsat8(LocalDate.parse(date), threshold.toInt)
      case List(date) => ImportLandsat8(LocalDate.parse(date))
      case _ => ImportLandsat8()
    }

    job.run
  }
}
