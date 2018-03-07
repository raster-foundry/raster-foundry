package com.azavea.rf.batch.landsat8

import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util._
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

import cats.effect.IO
import doobie.util.transactor.Transactor

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.util.control.Breaks._

@deprecated(message = "Use com.azavea.rf.batch.landsat8.airflow.ImportLandsat8C1 instead, as USGS changes LC8 storage rules: https://landsat.usgs.gov/landsat-collections", since = "https://github.com/azavea/raster-foundry/pull/1821")
case class ImportLandsat8(startDate: LocalDate = LocalDate.now(ZoneOffset.UTC), threshold: Int = 10)(implicit val xa: Transactor[IO]) extends Job {
  val name = ImportLandsat8.name

  /** Get S3 client per each call */
  def s3Client = S3(region = landsat8Config.awsRegion)
//
//  protected def scenesFromCsv(user: User, srcProj: CRS = CRS.fromName("EPSG:4326"), targetProj: CRS = CRS.fromName("EPSG:3857")): Future[ListBuffer[Option[Scene]]] = {
//    val reader = CSV.parse(landsat8Config.usgsLandsatUrl)
//    val iterator = reader.iterator()
//
//    val endDate = startDate + 1.day
//    val buffer = ListBuffer[Future[Option[Scene]]]()
//    val (_, indexToId) = CSV.getBiFunctions(iterator.next())
//
//    var counter = 0
//    breakable {
//      while(iterator.hasNext) {
//        val line = reader.readNext()
//
//        val row =
//          line
//            .zipWithIndex
//            .map { case (v, i) => indexToId(i) -> v }
//            .toMap
//
//        row.get("acquisitionDate") foreach { dateStr =>
//          val date = LocalDate.parse(dateStr)
//          if (startDate <= date && endDate > date) buffer += csvRowToScene(row, user, srcProj, targetProj)
//          else if (date < startDate) counter += 1
//        }
//
//        if (counter > threshold) break
//      }
//    }
//
//    Future.sequence(buffer)
//  }

  protected def getLandsatPath(productId: String): String = {
    val (wPath, wRow) = productId.substring(3, 6) -> productId.substring(6, 9)
    val path = s"L8/$wPath/$wRow/$productId"
    logger.debug(s"Constructed path: $path")
    path
  }

  protected def getLandsatUrl(productId: String): String = {
    val rootUrl = s"${landsat8Config.awsLandsatBase}${getLandsatPath(productId)}"
    logger.debug(s"Constructed Root URL: $rootUrl")
    rootUrl
  }

  // Getting the image size is the only place where the s3 object
  // is required to exist -- so handle the missing object by returning
  // a -1 for the image's size
  protected def sizeFromPath(tifPath: String, productId: String): Int = {
    val path = s"${getLandsatPath(productId)}/$tifPath"
    logger.info(s"Getting object size for path: $path")
    Try(
      s3Client.getObject(landsat8Config.bucketName, path).getObjectMetadata.getContentLength.toInt
    ) match {
      case Success(size) => size
      case Failure(_) => -1
    }
  }

  protected def createThumbnails(sceneId: UUID, productId: String): List[Thumbnail.Identified] = {
    val path = getLandsatUrl(productId)
    val smallUrl = s"$path/${productId}_thumb_small.jpg"
    val largeUrl = s"$path/${productId}_thumb_large.jpg"

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


//  @SuppressWarnings(Array("TraversableHead"))
//  protected def csvRowToScene(row: Map[String, String], user: User, srcProj: CRS = CRS.fromName("EPSG:4326"), targetProj: CRS = CRS.fromName("EPSG:3857")): Future[Option[Scene]] = Future {
//    val sceneId = UUID.randomUUID()
//    val productId = row("sceneID")
//    val landsatPath = getLandsatPath(productId)
//    val sceneName = s"L8 $landsatPath"
//
//    Scenes.sceneNameExistsForUser(sceneName, user).flatMap { exists =>
//      if (exists) Future(None)
//      else {
//        val ll = row("lowerLeftCornerLongitude").toDouble -> row("lowerLeftCornerLatitude").toDouble
//        val lr = row("lowerRightCornerLongitude").toDouble -> row("lowerRightCornerLatitude").toDouble
//        val ul = row("upperLeftCornerLongitude").toDouble -> row("upperLeftCornerLatitude").toDouble
//        val ur = row("upperRightCornerLongitude").toDouble -> row("upperRightCornerLatitude").toDouble
//
//        val srcCoords  = ll :: ul :: ur :: lr :: Nil
//        val srcPolygon = Polygon(Line(srcCoords :+ srcCoords.head))
//
//        val sortedByX = srcCoords.sortBy(_.x)
//        val sortedByY = srcCoords.sortBy(_.y)
//
//        val extent = Extent(
//          xmin = sortedByX.head.x,
//          ymin = sortedByY.head.y,
//          xmax = sortedByX.last.x,
//          ymax = sortedByY.last.y
//        )
//
//        val (transformedCoords, transformedExtent) = {
//          if (srcProj.equals(targetProj)) srcPolygon -> extent
//          else srcPolygon.reproject(srcProj, targetProj) -> extent.reproject(srcProj, targetProj)
//        }
//
//        val s3Url = s"${landsat8Config.awsLandsatBase}${landsatPath}/index.html"
//
//        if (!isUriExists(s3Url)) {
//          logger.warn(
//            "AWS and USGS are not always in sync. Try again in several hours.\n" +
//              s"If you believe this message is in error, check $s3Url manually."
//          )
//          Future(None)
//        } else {
//          val acquisitionDate =
//            row.get("acquisitionDate").map { dt =>
//              new java.sql.Timestamp(
//                LocalDate
//                  .parse(dt)
//                  .atStartOfDay(ZoneOffset.UTC)
//                  .toInstant
//                  .getEpochSecond * 1000l
//              )
//            }
//
//          val cloudCover = row.get("cloudCoverFull").map(_.toFloat)
//          val sunElevation = row.get("sunElevation").map(_.toFloat)
//          val sunAzimuth = row.get("sunAzimuth").map(_.toFloat)
//          val bands15m = landsat8Config.bandLookup.`15m`
//          val bands30m = landsat8Config.bandLookup.`30m`
//          val tags = List("Landsat 8", "GeoTIFF")
//          val sceneMetadata = row.filter { case (_, v) => v.nonEmpty }
//
//          val images = (
//            bands15m.map { band =>
//              val b = band.name.split(" - ").last
//              (15f, s"${productId}_B${b}.TIF", band)
//            } ++ bands30m.map { band =>
//              val b = band.name.split(" - ").last
//              (30f, s"${productId}_B${b}.TIF", band)
//            }
//            ).map {
//            case (resolution, tiffPath, band) =>
//              Image.Banded(
//                organizationId = landsat8Config.organizationUUID,
//                rawDataBytes = sizeFromPath(tiffPath, productId),
//                visibility = Visibility.Public,
//                filename = tiffPath,
//                sourceUri = s"${getLandsatUrl(productId)}/${tiffPath}",
//                owner = Some(systemUser),
//                scene = sceneId,
//                imageMetadata = Json.Null,
//                resolutionMeters = resolution,
//                metadataFiles = List(),
//                bands = List(band)
//              )
//          }
//
//          val scene = Scene.Create(
//            id = Some(sceneId),
//            organizationId = landsat8Config.organizationUUID,
//            ingestSizeBytes = 0,
//            visibility = Visibility.Public,
//            tags = tags,
//            datasource = landsat8Config.datasourceUUID,
//            sceneMetadata = sceneMetadata.asJson,
//            name = sceneName,
//            owner = Some(systemUser),
//            tileFootprint = targetProj.epsgCode.map(Projected(MultiPolygon(transformedExtent.toPolygon()), _)),
//            dataFootprint = targetProj.epsgCode.map(Projected(MultiPolygon(transformedCoords), _)),
//            metadataFiles = List(s"${landsat8Config.awsLandsatBase}${landsatPath}/${productId}_MTL.txt"),
//            images = images,
//            thumbnails = createThumbnails(sceneId, productId),
//            ingestLocation = None,
//            filterFields = SceneFilterFields(
//              cloudCover = cloudCover,
//              sunAzimuth = sunAzimuth,
//              sunElevation = sunElevation,
//              acquisitionDate = acquisitionDate
//            ),
//            statusFields = SceneStatusFields(
//              thumbnailStatus = JobStatus.Success,
//              boundaryStatus = JobStatus.Success,
//              ingestStatus = IngestStatus.NotIngested
//            )
//          )
//
//          val future =
//            Scenes.insertScene(scene, user).map(_.toScene).recover {
//              case e: PSQLException => {
//                logger.error(s"An error occurred during scene ${scene.id} import. Skipping...")
//                logger.error(e.stackTraceString)
//                sendError(e)
//                scene.toScene(user)
//              }
//            }
//
//          future onComplete {
//            case Success(s) => logger.info(s"Finished importing scene ${s.id}.")
//            case Failure(e) => {
//              logger.error(s"An error occurred during scene ${scene.id} import.")
//              logger.error(e.stackTraceString)
//              sendError(e)
//            }
//          }
//          future.map(Some(_))
//        }
//      }
//    }
//  }.flatMap(identity)

  def run: Unit = {
//    logger.info("Importing scenes...")
//    Users.getUserById(systemUser).flatMap { (userOpt) =>
//      scenesFromCsv(
//        userOpt.getOrElse {
//          val e = new Exception(s"User $systemUser doesn't exist.")
//          sendError(e)
//          throw e
//        })
//    } onComplete {
//      case Success(scenes) => {
//        val unskippedScenes = scenes.flatten
//        if(unskippedScenes.nonEmpty) logger.info(s"Successfully imported scenes: ${unskippedScenes.map(_.id.toString).mkString(", ")}.")
//        else if (scenes.nonEmpty) logger.info("All scenes were already imported")
//        else {
//          val e = new Exception(s"No scenes available for the ${startDate}")
//          logger.error(e.stackTraceString)
//          sendError(e)
//          stop
//          sys.exit(1)
//        }
//        stop
//      }
//      case Failure(e) => {
//        logger.error(e.stackTraceString)
//        sendError(e)
//        stop
//        sys.exit(1)
//      }
//    }
  }
}

object ImportLandsat8 {
  val name = "import_landsat8"

  def main(args: Array[String]): Unit = {
    ???
//    implicit val db = DB.DEFAULT
//
//    /** Since 30/04/2017 old LC8 collection is not updated */
//    val job = args.toList match {
//      case List(date, threshold) if LocalDate.parse(date) > LocalDate.of(2017, 4, 30) => ImportLandsat8C1(LocalDate.parse(date), threshold.toInt)
//      case List(date) if LocalDate.parse(date) > LocalDate.of(2017, 4, 30) => ImportLandsat8C1(LocalDate.parse(date))
//      case List(date, threshold) => ImportLandsat8(LocalDate.parse(date), threshold.toInt)
//      case List(date) => ImportLandsat8(LocalDate.parse(date))
//      case _ => ImportLandsat8()
//    }
//
//    job.run
  }
}
