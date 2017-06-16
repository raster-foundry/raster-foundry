package com.azavea.rf.batch.sentinel2.airflow

import com.azavea.rf.batch.Job
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.tables.{Scenes, Users}
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

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class ImportSentinel2(startDate: LocalDate = LocalDate.now(ZoneOffset.UTC))(implicit val database: DB) extends Job {
  import ImportSentinel2._

  val name = ImportSentinel2.name

  /** Get S3 client per each call */
  def s3Client = S3(region = sentinel2Config.awsRegion)

  def createImages(sceneId: UUID, tileInfo: Json, resolution: Float): List[Image.Banded] = {
    val tileInfoPath = tileInfo.hcursor.downField("path").as[String].toOption.getOrElse("")
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
          s"${tileInfoPath}/B8A.jp2"
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
        owner            = airflowUser.some,
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

  def findScenes(date: LocalDate, user: User): Future[List[Option[Scene]]] = {
    logger.info(s"Searching for new scenes on $date")
    Future.sequence(
      s3Client
        .listKeys(
          s3bucket  = sentinel2Config.bucketName,
          s3prefix  = s"products/${date.getYear}/${date.getMonthValue}/${date.getDayOfMonth}/",
          ext       = "productInfo.json",
          recursive = true
        )
        .toList
        .map { uri =>
          Future {
            val optList = for {
              json      <- s3Client.getObject(uri.getHost, uri.getPath.tail).getObjectContent.toJson
              tilesJson <- json.hcursor.downField("tiles").as[List[Json]].toOption
            } yield {
              tilesJson.flatMap { tileJson =>
                tileJson.hcursor.downField("path").as[String].toOption.map { tilePath =>
                  val sceneId = UUID.randomUUID()
                  val sceneName = s"S2 $tilePath"

                  Scenes.sceneNameExistsForUser(sceneName, user).flatMap { exists =>
                    if (exists) Future(None)
                    else {
                      logger.info(s"Starting scene creation for sentinel 2 scene: ${tilePath}")
                      val tileinfo =
                        s3Client
                          .getObject(sentinel2Config.bucketName, s"${tilePath}/tileInfo.json")
                          .getObjectContent
                          .toJson
                          .getOrElse(Json.Null)

                      val images = List(10f, 20f, 60f).map(createImages(sceneId, tileinfo, _)).reduce(_ ++ _)

                      val (tileFootprint, dataFootprint) =
                        multiPolygonFromJson(tileinfo, "tileGeometry", sentinel2Config.targetProjCRS) ->
                          multiPolygonFromJson(tileinfo, "tileDataGeometry", sentinel2Config.targetProjCRS)

                      val sceneMetadata: Map[String, String] = getSceneMetadata(tileinfo)

                      val awsBase = s"https://${sentinel2Config.bucketName}.s3.amazonaws.com"

                      val metadataFiles = List(
                        s"$awsBase/$tilePath/tileInfo.json",
                        s"$awsBase/$tilePath/metadata.xml",
                        s"$awsBase/$tilePath/productInfo.json"
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

                      val scene = Scene.Create(
                        id              = sceneId.some,
                        organizationId  = sentinel2Config.organizationUUID,
                        ingestSizeBytes = 0,
                        visibility      = Visibility.Public,
                        tags            = List("Sentinel-2", "JPEG2000"),
                        datasource      = sentinel2Config.datasourceUUID,
                        sceneMetadata   = sceneMetadata.asJson,
                        name            = sceneName,
                        owner           = airflowUser.some,
                        tileFootprint   = (sentinel2Config.targetProjCRS.epsgCode |@| tileFootprint).map { case (code, mp) => Projected(mp, code) },
                        dataFootprint   = (sentinel2Config.targetProjCRS.epsgCode |@| dataFootprint).map { case (code, mp) => Projected(mp, code) },
                        metadataFiles   = metadataFiles,
                        images          = images,
                        thumbnails      = createThumbnails(sceneId, tilePath),
                        ingestLocation  = None,
                        filterFields    = SceneFilterFields(
                          cloudCover      = cloudCover,
                          acquisitionDate = acquisitionDate
                        ),
                        statusFields = SceneStatusFields(
                          thumbnailStatus = JobStatus.Success,
                          boundaryStatus  = JobStatus.Success,
                          ingestStatus    = IngestStatus.NotIngested
                        )
                      )

                      logger.info(s"Importing scene $sceneId...")
                      val future =
                        Scenes.insertScene(scene, user).map(_.toScene).recover {
                          case e: PSQLException => {
                            logger.error(s"An error occurred during scene $sceneId import. Skipping...")
                            logger.error(e.stackTraceString)
                            sendError(e)
                            scene.toScene(user)
                          }
                        }

                      future onComplete {
                        case Success(s) => logger.info(s"Finished importing scene ${s.id}.")
                        case Failure(e) => {
                          logger.error(s"An error occurred during scene $sceneId import.")
                          logger.error(e.stackTraceString)
                          sendError(e)
                        }
                      }
                      future.map(Some(_))
                    }
                  }
                }
              }
            }
            Future.sequence(optList.getOrElse(Nil))
          }.flatMap(identity)
        }
    ).map(_.flatten)
  }

  def run: Unit = {
    logger.info("Importing scenes...")
    Users.getUserById(airflowUser).flatMap { userOpt =>
      findScenes(startDate, userOpt.getOrElse {
        val e = new Exception(s"User $airflowUser doesn't exist.")
        sendError(e)
        throw e
      })
    } onComplete {
      case Success(scenes) => {
        val unskippedScenes = scenes.flatten
        if(unskippedScenes.nonEmpty) logger.info(s"Successfully imported scenes: ${unskippedScenes.map(_.id.toString).mkString(", ")}.")
        else if (scenes.nonEmpty) logger.info("All scenes were already imported")
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
    implicit val db = DB.DEFAULT

    val job = args.toList match {
      case List(date) => ImportSentinel2(LocalDate.parse(date))
      case _ => ImportSentinel2()
    }

    job.run
  }
}
