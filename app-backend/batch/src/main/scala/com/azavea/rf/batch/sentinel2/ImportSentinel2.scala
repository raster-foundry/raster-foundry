package com.azavea.rf.batch.sentinel2

import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.tables.{Scenes, Users}
import com.azavea.rf.datamodel._

import io.circe._
import io.circe.syntax._
import cats.implicits._
import geotrellis.proj4.CRS
import geotrellis.slick.Projected
import geotrellis.vector._
import org.postgresql.util.PSQLException

import java.security.InvalidParameterException
import java.time.{LocalDate, ZoneOffset}
import java.util.UUID

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class ImportSentinel2(startDate: LocalDate = LocalDate.now(ZoneOffset.UTC))(implicit val database: DB) extends Job {
  implicit def optionToString(opt: Option[String]): String = opt.getOrElse("")

  def getTileInfo(path: String): Json =
    S3.getObject(sentinel2Config.bucketName, path).getObjectContent.toJson.getOrElse(Json.Null)

  val name = "import_sentinel2"

  def createImages(sceneId: UUID, tileInfo: Json, resolution: Float): List[Image.Banded] = {
    val tileInfoPath = tileInfo.hcursor.downField("path").as[String].toOption.getOrElse("")
    logger.info(s"Creating images for $tileInfoPath with resolution $resolution")

    val keys = resolution match {
      case 10f =>
        List(
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B02.jp2"),
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B03.jp2"),
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B04.jp2"),
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B08.jp2")
        )
      case 20f =>
        List(
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B05.jp2"),
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B06.jp2"),
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B07.jp2"),
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B8A.jp2")
        )
      case 60f =>
        List(
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B01.jp2"),
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B09.jp2"),
          S3.getObject(sentinel2Config.bucketName, s"${tileInfoPath}/B10.jp2")
        )
      case _ =>
        throw new InvalidParameterException(s"Unable to create images for $tileInfoPath at resolution $resolution")
    }

    keys.map { obj =>
      val filename = obj.getKey.split("/").last
      val imageBand = filename.split("\\.").head
      Image.Banded(
        organizationId   = sentinel2Config.organizationUUID,
        rawDataBytes     = obj.getObjectMetadata.getContentLength.toInt,
        visibility       = Visibility.Public,
        filename         = filename,
        sourceUri        = s"${sentinel2Config.baseHttpPath}/${obj.getKey}",
        owner            = airflowUser.some,
        scene            = sceneId,
        imageMetadata    = Json.Null,
        resolutionMeters = resolution,
        metadataFiles    = List(),
        bands            = Seq(sentinel2Config.bandByName(imageBand)).flatten
      )
    }
  }

  def multiPolygonFromJson(tileinfo: Json, key: String): Option[MultiPolygon] = {
    val geom = tileinfo.hcursor.downField(key)
    val coords = geom.downField("coordinates").focus.flatMap(_.as[List[(Double, Double)]].toOption)
    val srcProj = geom.downField("crs").downField("properties").downField("name").focus.flatMap(_.as[String].toOption).map(CRS.fromName)

    (srcProj |@| coords).map { case (sourceProjCRS, crds) =>
      MultiPolygon(Polygon(Line(crds.map(_.reproject(sourceProjCRS, sentinel2Config.targetProjCRS)))))
    }
  }

  def createThumbnails(sceneId: UUID, tilePath: String): List[Thumbnail.Identified] = {
    val keyPath: String = s"$tilePath/preview.jpg"
    val thumbnalUrl = s"${sentinel2Config.baseHttpPath}/$keyPath"

    if (!isUriExists(thumbnalUrl)) Nil
    else
      Thumbnail.Identified(
        id = None,
        organizationId = UUID.fromString(landsat8Config.organization),
        thumbnailSize  = ThumbnailSize.Square,
        widthPx        = 343,
        heightPx       = 343,
        sceneId        = sceneId,
        url            = thumbnalUrl
      ) :: Nil
  }

  def findScenes(date: LocalDate, user: User): Future[List[Scene]] = {
    logger.info(s"Searching for new scenes on $date")
    val prefix = f"products/${date.getYear}/${date.getMonthValue}%02d/${date.getDayOfMonth}%02d/"

    Future.sequence(
        S3
          .listKeys(sentinel2Config.bucketName, prefix, "productInfo.json")
          .toList
          .map { uri =>
            Future {
              val optList = for {
                json      <- S3.getObject(uri.getHost, uri.getPath, sentinel2Config.awsRegion).getObjectContent.toJson
                tilesJson <- json.hcursor.downField("tiles").as[List[Json]].toOption
              } yield {
                tilesJson.flatMap { tileJson =>
                  tileJson.hcursor.downField("path").as[String].toOption.map { tilePath =>
                    val sceneId = UUID.randomUUID()
                    logger.info(s"Starting scene creation for sentinel 2 scene: ${tilePath}")
                    val metadataFile = s"${tilePath}/tileInfo.json"
                    val tileinfo = getTileInfo(metadataFile)

                    val images =
                      createImages(sceneId, tileinfo, 10f) ++
                      createImages(sceneId, tileinfo, 20f) ++
                      createImages(sceneId, tileinfo, 60f)

                    val (tileFootprint, dataFootprint) =
                      multiPolygonFromJson(tileinfo, "tileGeometry") -> multiPolygonFromJson(tileinfo, "tileDataGeometry")

                    val awsBase = s"https://${sentinel2Config.bucketName}.s3.amazonaws.com"

                    val metadataFiles = List(
                      s"$awsBase/$tilePath/tileInfo.json",
                      s"$awsBase/$tilePath/metadata.xml",
                      s"$awsBase/$tilePath/productInfo.json"
                    )

                    val cloudCover = tileinfo.hcursor.downField("dataCoveragePercentage").as[String].toOption.map(_.toFloat)
                    val acquisitionDate =
                      tileinfo.hcursor.downField("timestamp").as[String].toOption.map { dt =>
                        new java.sql.Timestamp(
                          LocalDate
                            .parse(dt)
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant
                            .getEpochSecond * 1000l
                        )
                      }

                    val sceneMetadata = Map[String, String](
                      ("path", tileinfo.hcursor.downField("path").as[String].toOption),
                      ("timeStamp", tileinfo.hcursor.downField("timeStamp").as[String].toOption),
                      ("utmZone", tileinfo.hcursor.downField("utmZone").as[String].toOption),
                      ("latitudeBand", tileinfo.hcursor.downField("latitudeBand").as[String].toOption),
                      ("gridSquare", tileinfo.hcursor.downField("gridSquare").as[String].toOption),
                      ("dataCoveragePercentage", tileinfo.hcursor.downField("dataCoveragePercentage").as[String].toOption),
                      ("cloudyPixelPercentage", tileinfo.hcursor.downField("cloudyPixelPercentage").as[String].toOption),
                      ("productName", tileinfo.hcursor.downField("productName").as[String].toOption),
                      ("productPath", tileinfo.hcursor.downField("productPath").as[String].toOption)
                    )

                    val scene = Scene.Create(
                      id              = sceneId.some,
                      organizationId  = sentinel2Config.organizationUUID,
                      ingestSizeBytes = 0,
                      visibility      = Visibility.Public,
                      tags            = List("Sentinel-2", "JPEG2000"),
                      datasource      = sentinel2Config.datasourceUUID,
                      sceneMetadata   = sceneMetadata.asJson,
                      name            = s"S2 $tilePath",
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
                    future
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
        stop
        throw e
      })
    } onComplete {
      case Success(scenes) => {
        if(scenes.nonEmpty) logger.info(s"Successfully imported scenes: ${scenes.map(_.id.toString).mkString(", ")}.")
        else {
          val e = new Exception(s"No scenes available for the ${startDate}")
          sendError(e)
          stop
          throw e
        }
        stop
      }
      case Failure(e) => {
        logger.error(e.stackTraceString)
        sendError(e)
        stop
      }
    }
  }
}

object ImportSentinel2 {
  val name = "import_sentinel2"

  def main(args: Array[String]): Unit = {
    implicit val db = DB.DEFAULT

    val job = args.toList match {
      case List(date) => ImportSentinel2(LocalDate.parse(date))
      case _ => ImportSentinel2()
    }

    job.run
  }
}
