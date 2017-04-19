package com.azavea.rf.batch.landsat8

import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util.S3
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.tables._
import com.azavea.rf.datamodel._

import io.circe._
import io.circe.syntax._
import geotrellis.proj4.CRS
import geotrellis.slick.Projected
import geotrellis.vector._
import jp.ne.opt.chronoscala.Imports._

import java.time.{LocalDate, ZoneOffset}
import java.util.UUID

import scala.io.Source
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import scala.util.control.Breaks._

case class ImportLandsat8(startDate: LocalDate = LocalDate.now(ZoneOffset.UTC), threshold: Int = 10)(implicit val database: DB) extends Job {
  val name = "import_landsat8"

  @SuppressWarnings(Array("OptionGet"))
  protected def getCsv: ListBuffer[Map[String, String]] = {
    val source = Source.fromURL(landsat8Config.usgsLandsatUrl, "UTF-8")
    val lines = source.getLines()

    val endDate = startDate + 1.day
    val buffer = ListBuffer[Map[String, String]]()
    val idToIndex: Map[String, Int] = lines.next().split(",").map(_.trim).zipWithIndex.toMap
    val indexToId = idToIndex map { case (v, i) => i -> v }

    var counter = 0
    breakable {
      lines foreach { line =>
        val row =
          line
            .split(",")
            .map(_.trim)
            .zipWithIndex
            .map { case (v, i) => indexToId(i) -> v }
            .toMap

        row.get("acquisitionDate") foreach { dateStr =>
          val date = LocalDate.parse(dateStr)
          if (startDate <= date && endDate > date) buffer += row
          else if (date < startDate) counter += 1
        }

        if (counter > threshold) break
      }
    }

    buffer
  }

  protected def getLandsatPath(landsatId: String) = {
    val (wPath, wRow) = landsatId.substring(3, 6) -> landsatId.substring(6, 9)
    val path = s"L8/$wPath/$wRow/$landsatId"
    logger.debug(s"Constructed path: $path")
    path
  }

  protected def getLandsatUrl(landsatId: String) = {
    val rootUrl = s"https://landsat-pds.s3.amazonaws.com/${getLandsatPath(landsatId)}"
    logger.debug(s"Constructed Root URL: $rootUrl")
    rootUrl
  }

  protected def s3ObjExists(url: String): Boolean = {
    try {
      Source.fromURL(url, "UTF-8")
      true
    } catch {
      case _: java.io.FileNotFoundException => false
    }
  }

  protected def sizeFromPath(tifPath: String, landsatId: String): Int = {
    val path = s"${getLandsatPath(landsatId)}$tifPath"
    logger.info(s"Getting object size for path: $path")
    S3.getObject(landsat8Config.bucketName, path).getObjectMetadata.getContentLength.toInt
  }

  protected def createThumbnails(sceneId: UUID, landsatId: String) = {
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
  protected def csvRowToScene(row: Map[String, String], srcProj: CRS = CRS.fromName("EPSG:4326"), targetProj: CRS = CRS.fromName("EPSG:4326")): Future[Option[Scene.Create]] = Future {
    val sceneId = UUID.randomUUID()
    val landsatId = row("sceneID")

    val ll = row("lowerLeftCornerLongitude").toDouble -> row("lowerLeftCornerLatitude").toDouble
    val lr = row("lowerRightCornerLongitude").toDouble -> row("lowerRightCornerLatitude").toDouble
    val ul = row("upperLeftCornerLongitude").toDouble -> row("upperLeftCornerLatitude").toDouble
    val ur = row("upperRightCornerLongitude").toDouble -> row("upperRightCornerLatitude").toDouble

    val srcCoords = ll :: lr :: ul :: ur :: Nil

    val sortedByX = srcCoords.sortBy(_.x)
    val sortedByY = srcCoords.sortBy(_.y)

    val extent = Extent(
      xmin = sortedByX.head.x,
      ymin = sortedByY.head.y,
      xmax = sortedByX.last.x,
      ymax = sortedByY.last.y
    )

    val (transformedCoords, transformedExtent) = {
      if (srcProj.equals(targetProj)) Polygon(Line(srcCoords :+ srcCoords.head)) -> extent
      else
        Polygon(Line(srcCoords.map {
          _.reproject(srcProj, targetProj)
        })) -> extent.reproject(srcProj, targetProj)
    }

    val landsatPath = getLandsatPath(landsatId)


    if (!s3ObjExists(s"${landsat8Config.awsLandsatBase}${landsatPath}index.html")) {
      logger.warn(
        "AWS and USGS are not always in sync. Try again in several hours.\n" +
          s"If you believe this message is in error, check ${landsat8Config.awsLandsatBase}${landsatPath} manually."
      )
      None
    } else {
      val acquisitionDate = Some(new java.sql.Timestamp(
        LocalDate
          .parse(row("acquisitionDate"))
          .atStartOfDay(ZoneOffset.UTC)
          .toInstant
          .getEpochSecond * 1000l
      ))

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
            sourceUri = s"${getLandsatUrl(landsatId)}${tiffPath}",
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
        tileFootprint = targetProj.epsgCode.map(Projected(MultiPolygon(transformedExtent.toPolygon()), _)),
        dataFootprint = targetProj.epsgCode.map(Projected(MultiPolygon(transformedCoords), _)),
        metadataFiles = List(s"${landsat8Config.awsLandsatBase}${landsatPath}${landsatId}_MTL.txt"),
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
    val future = Users.getUserById(airflowUser).flatMap { userOpt =>
      val user = userOpt.getOrElse(throw new Exception(s"User $airflowUser doesn't exist."))

      Future
        .sequence(getCsv.map(csvRowToScene(_)))
        .map(_.flatten)
        .flatMap { scenes =>
          Future.sequence(scenes.map { scene =>
            val id = scene.id.map(_.toString).getOrElse("")
            logger.info(s"Importing scene $id...")
            val future = Scenes.insertScene(scene, user)
            future onComplete {
              case Success(s) => logger.info(s"Finished importing scene ${s.id}.")
              case Failure(e) => {
                logger.error(s"An error occurred during scene $id import.")
                throw e
              }
            }
            future
          })
        }
    }

    future onComplete {
      case Success(scenes) => logger.info(s"Successfully imported scenes: ${scenes.map(_.id.toString).mkString(", ")}.")
      case Failure(e) => throw e
    }

    Await.result(future, Duration.Inf)
  }
}

object ImportLandsat8 {
  val name = "import_landsat8"

  def main(args: Array[String]): Unit = {
    implicit val db = DB.DEFAULT

    val job = args.toList match {
      case List(date, threshold) => ImportLandsat8(LocalDate.parse(date), threshold.toInt)
      case List(date) => ImportLandsat8(LocalDate.parse(date))
      case _ => ImportLandsat8()
    }

    job.run
  }
}
