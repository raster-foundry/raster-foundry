package com.azavea.rf.batch.sentinel2

import java.net.URI

import com.azavea.rf.batch.Job
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.{SceneDao, UserDao}
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel._
import com.azavea.rf.batch.util._
import io.circe._
import io.circe.syntax._
import cats.implicits._
import geotrellis.proj4.CRS
import geotrellis.slick.Projected
import geotrellis.vector._
import geotrellis.vector.io._
import org.postgresql.util.PSQLException
import java.security.InvalidParameterException
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import java.util.UUID

import cats.effect.IO
import doobie.{ConnectionIO, Fragment, Fragments}
import doobie.implicits._
import doobie.util.transactor.Transactor

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class ImportSentinel2(startDate: LocalDate = LocalDate.now(ZoneOffset.UTC))(implicit val xa: Transactor[IO]) extends Job {

  // To resolve an ambiguous implicit
  import doobie.free.connection.AsyncConnectionIO
  import ImportSentinel2._

  val name = ImportSentinel2.name

  type SceneName = String

  /** Get S3 client per each call */
  def s3Client = S3(region = sentinel2Config.awsRegion)

  def createImages(sceneId: UUID, infoPath: Option[String], resolution: Float): List[Image.Banded] = {
    val tileInfoPath = infoPath.getOrElse("")
    logger.info(s"Creating images for $tileInfoPath with resolution $resolution")

    val keys = resolution match {
      case 10f =>
        List(
          s"${tileInfoPath}/B02.jp2",
          s"${tileInfoPath}/B03.jp2",
          s"${tileInfoPath}/B04.jp2",
          s"${tileInfoPath}/B08.jp2"
        )
      case 20f =>
        List(
          s"${tileInfoPath}/B05.jp2",
          s"${tileInfoPath}/B06.jp2",
          s"${tileInfoPath}/B07.jp2",
          s"${tileInfoPath}/B8A.jp2",
          s"${tileInfoPath}/B11.jp2",
          s"${tileInfoPath}/B12.jp2"
        )
      case 60f =>
        List(
          s"${tileInfoPath}/B01.jp2",
          s"${tileInfoPath}/B09.jp2",
          s"${tileInfoPath}/B10.jp2"
        )
      case _ =>
        throw new InvalidParameterException(s"Unable to create images for $tileInfoPath at resolution $resolution")
    }

    keys.map { obj =>
      val filename = obj.split("/").last
      val imageBand = filename.split("\\.").head
      val objMetadata = s3Client.client.getObjectMetadata(sentinel2Config.bucketName, obj)
      Image.Banded(
        organizationId   = sentinel2Config.organizationUUID,
        rawDataBytes     = objMetadata.getContentLength.toInt,
        visibility       = Visibility.Public,
        filename         = filename,
        sourceUri        = s"${sentinel2Config.baseHttpPath}${obj}",
        owner            = systemUser.some,
        scene            = sceneId,
        imageMetadata    = Json.Null,
        resolutionMeters = resolution,
        metadataFiles    = List(),
        bands            = Seq(sentinel2Config.bandByName(imageBand)).flatten
      )
    }
  }

  def createThumbnails(sceneId: UUID, tilePath: String): List[Thumbnail.Identified] = {
    val keyPath: String = s"$tilePath/preview.jpg"
    val thumbnailUrl = s"${sentinel2Config.baseHttpPath}$keyPath"

    if (!isUriExists(thumbnailUrl)) Nil
    else
      Thumbnail.Identified(
        id = None,
        organizationId = UUID.fromString(landsat8Config.organization),
        thumbnailSize  = ThumbnailSize.Square,
        widthPx        = 343,
        heightPx       = 343,
        sceneId        = sceneId,
        url            = thumbnailUrl
      ) :: Nil
  }

  def getSentinel2Products(date: LocalDate): List[URI] = {
    s3Client
      .listKeys(
        s3bucket  = sentinel2Config.bucketName,
        s3prefix  = s"products/${date.getYear}/${date.getMonthValue}/${date.getDayOfMonth}/",
        ext       = "productInfo.json",
        recursive = true
      ).toList
  }

  def getExistingScenes(date: LocalDate, datasourceUUID: UUID): ConnectionIO[List[SceneName]] = {
    val idsQuery =
      sql" select id from scenes where date(acquisition_date) = ${date} and datasource_id = ${datasourceUUID}"
    idsQuery.query[SceneName].stream.compile.toList
  }

  /** Because it makes scenes -- get it? */
  def riot(existingScenes: List[SceneName], scenePath: String, datasourceUUID: UUID): Option[Scene.Create] = {
      val sceneId = UUID.randomUUID()
      val images = List(10f, 20f, 60f).map(createImages(sceneId, scenePath.some, _)).reduce(_ ++ _)
      val thumbnails = createThumbnails(sceneId, scenePath)
      val sceneName = s"S2 ${scenePath}"
      if (existingScenes.contains(sceneName)) {
        logger.info(s"Skipping scene creation. Scene already exists: ${scenePath} - ${sceneName}")
        None
      } else {
        logger.info(s"Starting scene creation: ${scenePath}- ${sceneName}")
        val tileinfo =
          s3Client
            .getObject(sentinel2Config.bucketName, s"${scenePath}/tileInfo.json")
            .getObjectContent
            .toJson
            .getOrElse(Json.Null)
        logger.info(s"Getting scene metadata for ${scenePath}")
        val sceneMetadata: Map[String, String] = getSceneMetadata(tileinfo)
        val datasourcePrefix = "S2"
        logger.info(s"${datasourcePrefix} - Extracting polygons for ${scenePath}")
        val tileFootprint = multiPolygonFromJson(tileinfo, "tileGeometry", sentinel2Config.targetProjCRS)
        val dataFootprint = multiPolygonFromJson(tileinfo, "tileDataGeometry", sentinel2Config.targetProjCRS)
        val awsBase = s"https://${sentinel2Config.bucketName}.s3.amazonaws.com"
        val metadataFiles = List(
          s"$awsBase/$scenePath/tileInfo.json",
          s"$awsBase/$scenePath/metadata.xml",
          s"$awsBase/$scenePath/productInfo.json"
        )
        val cloudCover = sceneMetadata.get("dataCoveragePercentage").map(_.toFloat)
        val acquisitionDate = sceneMetadata.get("timeStamp").map { dt =>
          new java.sql.Timestamp(
            ZonedDateTime
              .parse(dt)
              .toInstant
              .getEpochSecond * 1000l
          )
        }
        logger.info(s"${datasourcePrefix} - Creating scene case class ${scenePath}")
        Scene.Create(
          id = sceneId.some,
          organizationId = sentinel2Config.organizationUUID,
          ingestSizeBytes = 0,
          visibility = Visibility.Public,
          tags = List("Sentinel-2", "JPEG2000"),
          datasource = datasourceUUID,
          sceneMetadata = sceneMetadata.asJson,
          name = sceneName,
          owner = systemUser.some,
          tileFootprint = (sentinel2Config.targetProjCRS.epsgCode |@| tileFootprint).map {
            case (code, mp) => Projected(mp, code)
          },
          dataFootprint = (sentinel2Config.targetProjCRS.epsgCode |@| dataFootprint).map {
            case (code, mp) => Projected(mp, code)
          },
          metadataFiles = metadataFiles,
          images = images,
          thumbnails = createThumbnails(sceneId, scenePath),
          ingestLocation = None,
          filterFields = SceneFilterFields(
            cloudCover = cloudCover,
            acquisitionDate = acquisitionDate
          ),
          statusFields = SceneStatusFields(
            thumbnailStatus = JobStatus.Success,
            boundaryStatus = JobStatus.Success,
            ingestStatus = IngestStatus.NotIngested
          )
        ).some
      }
  }

  def findScenes(date: LocalDate, keys: List[URI], user: User, datasourceUUID: UUID): ConnectionIO[List[Scene.WithRelated]] = {
    val scenesIo: ConnectionIO[List[Scene.Create]] = getExistingScenes(date, datasourceUUID) map {
      case ids: List[SceneName] => {
        keys map {
          uri:URI => {
            for {
              json <- s3Client.getObject(uri.getHost, uri.getPath.tail).getObjectContent.toJson
              tilesJson <- json.hcursor.downField("tiles").as[List[Json]].toOption
            } yield {
              tilesJson.flatMap { tileJson =>
                tileJson.hcursor.downField("path").as[String].toOption.map {
                  scenePath => riot(ids, scenePath, datasourceUUID)
                }
              }.flatten
            }
          }.foldLeft(List.empty[Scene.Create])(_ combine _)
        }
      }.flatten
    }

    logger.info(s"Inserting scenes for ${date}")

    scenesIo flatMap { sceneList: List[Scene.Create] =>
      sceneList.traverse({ SceneDao.insert(_, user) })
    }
  }

  def run: Unit = {
    logger.info(s"Importing Sentinel 2 scenes for ${startDate}")
    val keys = getSentinel2Products(startDate)
    val scenesIO:ConnectionIO[List[Scene.WithRelated]] =
      UserDao.getUserById(systemUser) flatMap { (mbUser: Option[User]) =>
        mbUser match {
          case Some(u) =>
            findScenes(startDate, keys, u, sentinel2Config.datasourceUUID)
          case None =>
            throw new Exception(s"${systemUser} could not be found -- probably the database is borked")
        }
      }
    scenesIO.transact(xa).unsafeRunSync
    logger.info("Scenes imported successfully")
  }
}


object ImportSentinel2 {
  val name = "import_sentinel2"

  def multiPolygonFromJson(tileinfo: Json, key: String, targetCrs: CRS = CRS.fromName("EPSG:3857")): Option[MultiPolygon] = {
    val geom = tileinfo.hcursor.downField(key)
    val polygon = geom.focus.map(_.noSpaces.parseGeoJson[Polygon])
    val srcProj =
      geom
        .downField("crs")
        .downField("properties")
        .downField("name")
        .focus
        .flatMap(_.as[String].toOption)
        .map { crs => CRS.fromName(s"EPSG:${crs.split(":").last}") }

    (srcProj |@| polygon).map { case (sourceProjCRS, poly) =>
      MultiPolygon(poly.reproject(sourceProjCRS, targetCrs))
    }
  }

  def getSceneMetadata(tileinfo: Json): Map[String, String] = {
    /** Required for sceneMetadata collection, to unbox Options */
    implicit def optionToString(opt: Option[String]): String = opt.getOrElse("")
    implicit def optionTToString[T](opt: Option[T]): String = opt.map(_.toString)

    Map(
      ("path", tileinfo.hcursor.downField("path").as[String].toOption),
      ("timeStamp", tileinfo.hcursor.downField("timestamp").as[String].toOption),
      ("utmZone", tileinfo.hcursor.downField("utmZone").as[Int].toOption),
      ("latitudeBand", tileinfo.hcursor.downField("latitudeBand").as[String].toOption),
      ("gridSquare", tileinfo.hcursor.downField("gridSquare").as[String].toOption),
      ("dataCoveragePercentage", tileinfo.hcursor.downField("dataCoveragePercentage").as[Double].toOption),
      ("cloudyPixelPercentage", tileinfo.hcursor.downField("cloudyPixelPercentage").as[Double].toOption),
      ("productName", tileinfo.hcursor.downField("productName").as[String].toOption),
      ("productPath", tileinfo.hcursor.downField("productPath").as[String].toOption)
    )
  }

  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa
    val job = args.toList match {
      case List(date) => ImportSentinel2(LocalDate.parse(date))
      case _ => ImportSentinel2()
    }

    job.run
  }
}
