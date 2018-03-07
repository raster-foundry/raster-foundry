package com.azavea.rf.batch.landsat8

import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util._
import com.azavea.rf.database.{SceneDao, SceneWithRelatedDao, UserDao}
import doobie.util.transactor.Transactor
import doobie.Fragments._
import com.azavea.rf.database.filter.Filterables._
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

import cats.implicits._
import cats.effect.IO
import com.azavea.rf.database.util.RFTransactor
import doobie.free.connection.ConnectionIO
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import Fragments._
import cats.free.Free
import doobie.free.connection

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.util.control.Breaks._


case class ImportLandsat8C1(startDate: LocalDate = LocalDate.now(ZoneOffset.UTC), threshold: Int = 10)(implicit val xa: Transactor[IO]) extends Job {
  val name = ImportLandsat8C1.name

  /** Get S3 client per each call */
  def s3Client = S3(region = landsat8Config.awsRegion)

  protected def scenesFromCsv(user: User, srcProj: CRS = CRS.fromName("EPSG:4326"), targetProj: CRS = CRS.fromName("EPSG:3857")): ConnectionIO[List[Option[Scene.WithRelated]]] = {
    val reader = CSV.parse(landsat8Config.usgsLandsatUrlC1)
    val iterator = reader.iterator()

    val endDate = startDate + 1.day
    val buffer = ListBuffer[ConnectionIO[Option[Scene.WithRelated]]]()
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
          if (startDate <= date && endDate > date) buffer += csvRowToScene(row, user, srcProj, targetProj)
          else if (date < startDate) counter += 1
        }
        if (counter > threshold) break
      }
    }
    buffer.toList.sequence
  }

  protected def getLandsatPath(productId: String): String = {
    val p = productId.split("_")(2)
    val (wPath, wRow) = p.substring(0, 3) -> p.substring(3, 6)
    val path = s"L8/$wPath/$wRow/$productId"
    logger.debug(s"Constructed path: $path")
    path
  }

  protected def getLandsatUrl(productId: String): String = {
    val rootUrl = s"${landsat8Config.awsLandsatBaseC1}${getLandsatPath(productId)}"
    logger.debug(s"Constructed Root URL: $rootUrl")
    rootUrl
  }

  // Getting the image size is the only place where the s3 object
  // is required to exist -- so handle the missing object by returning
  // a -1 for the image's size
  protected def sizeFromPath(tifPath: String, productId: String): Int = {
    val path = s"c1/${getLandsatPath(productId)}/$tifPath"
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


  @SuppressWarnings(Array("TraversableHead"))
  protected def csvRowToScene(
    row: Map[String, String], user: User, srcProj: CRS = CRS.fromName("EPSG:4326"),
    targetProj: CRS = CRS.fromName("EPSG:3857")): ConnectionIO[Option[Scene.WithRelated]] = {

    val sceneId = UUID.randomUUID()
    val productId = row("LANDSAT_PRODUCT_ID")
    val landsatPath = getLandsatPath(productId)
    val sceneName = s"L8 $landsatPath"

    val x: ConnectionIO[Option[Scene.WithRelated]] = for {
      maybeScene <- SceneDao.query.filter(fr"name = ${sceneName}").filter(fr"owner = ${user.id}").selectOption
      sceneInsert <- {
        maybeScene match {
          case Some(scene) => {
            None.pure[ConnectionIO]
          }
          case _ => {
            createSceneFromRow(row, user, srcProj, targetProj, sceneId, productId, landsatPath) match {
              case Some(scene) => SceneDao.insertMaybe(scene, user)
              case _ => None.pure[ConnectionIO]
            }
          }
        }
      }
    } yield {
      sceneInsert
    }
    x
  }

  private def createSceneFromRow(row: Map[String, String], user: User, srcProj: CRS, targetProj: CRS, sceneId: UUID, productId: String, landsatPath: String) = {
    val ll = row("lowerLeftCornerLongitude").toDouble -> row("lowerLeftCornerLatitude").toDouble
    val lr = row("lowerRightCornerLongitude").toDouble -> row("lowerRightCornerLatitude").toDouble
    val ul = row("upperLeftCornerLongitude").toDouble -> row("upperLeftCornerLatitude").toDouble
    val ur = row("upperRightCornerLongitude").toDouble -> row("upperRightCornerLatitude").toDouble

    val srcCoords = ll :: ul :: ur :: lr :: Nil
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

    val s3Url = s"${landsat8Config.awsLandsatBaseC1}${landsatPath}/index.html"
  
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

      val images = (
        bands15m.map { band =>
          val b = band.name.split(" - ").last
          (15f, s"${productId}_B${b}.TIF", band)
        } ++ bands30m.map { band =>
          val b = band.name.split(" - ").last
          (30f, s"${productId}_B${b}.TIF", band)
        }
        ).map {
        case (resolution, tiffPath, band) =>
          Image.Banded(
            organizationId = landsat8Config.organizationUUID,
            rawDataBytes = sizeFromPath(tiffPath, productId),
            visibility = Visibility.Public,
            filename = tiffPath,
            sourceUri = s"${getLandsatUrl(productId)}/${tiffPath}",
            owner = Some(systemUser),
            scene = sceneId,
            imageMetadata = Json.Null,
            resolutionMeters = resolution,
            metadataFiles = List(),
            bands = List(band)
          )
      }

      val scene = Scene.Create(
        id = Some(sceneId),
        organizationId = landsat8Config.organizationUUID,
        ingestSizeBytes = 0,
        visibility = Visibility.Public,
        tags = tags,
        datasource = landsat8Config.datasourceUUID,
        sceneMetadata = sceneMetadata.asJson,
        name = landsatPath,
        owner = Some(systemUser),
        tileFootprint = targetProj.epsgCode.map(Projected(MultiPolygon(transformedExtent.toPolygon()), _)),
        dataFootprint = targetProj.epsgCode.map(Projected(MultiPolygon(transformedCoords), _)),
        metadataFiles = List(s"${landsat8Config.awsLandsatBaseC1}${landsatPath}/${productId}_MTL.txt"),
        images = images,
        thumbnails = createThumbnails(sceneId, productId),
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
      )
      Some(scene)
    }
  }

  def run(implicit xa: Transactor[IO]): Unit = {
    logger.info("Importing scenes...")

    val userQuery = UserDao.query.filter(fr"id = ${systemUser}").select
    val insertedScenes = for {
      user <- userQuery
      scenes <- scenesFromCsv(user)
    } yield {
      scenes.flatten
    }

    insertedScenes.transact(xa).unsafeToFuture onComplete {
      case Success(unskippedScenes) => {
        if (unskippedScenes.nonEmpty) logger.info(s"Successfully imported scenes: ${unskippedScenes.map(_.id.toString).mkString(", ")}.")
        else if (unskippedScenes.nonEmpty) logger.info("All scenes were already imported")
        else {
          val e = new Exception(s"No scenes available for the ${startDate}")
          logger.error(e.stackTraceString)
          sendError(e)
          stop
          sys.exit(1)
        }
        stop
      }

      case Failure(e) => {
        logger.error(e.stackTraceString)
        sendError(e)
        stop
        sys.exit(1)
      }
    }
  }
}

object ImportLandsat8C1 {
  val name = "import_landsat8_c1"

  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa

    val job = args.toList match {
      case List(date, threshold) => ImportLandsat8C1(LocalDate.parse(date), threshold.toInt)
      case List(date) => ImportLandsat8C1(LocalDate.parse(date))
      case _ => ImportLandsat8C1()
    }

    job.run
  }
}
