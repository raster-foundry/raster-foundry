package com.azavea.rf.batch.sentinel2

import java.net.URI

import com.azavea.rf.batch.Job
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
import doobie.util.transactor.Transactor

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class ImportSentinel2(startDate: LocalDate = LocalDate.now(ZoneOffset.UTC))(implicit val xa: Transactor[IO]) extends Job {

  import ImportSentinel2._

  val name = ImportSentinel2.name

  /** Get S3 client per each call */
  //  def s3Client = S3(region = sentinel2Config.awsRegion)
  //
  //  def createImages(sceneId: UUID, tileInfo: Json, resolution: Float): List[Image.Banded] = {
  //    val tileInfoPath = tileInfo.hcursor.downField("path").as[String].toOption.getOrElse("")
  //    logger.info(s"Creating images for $tileInfoPath with resolution $resolution")
  //
  //    val keys = resolution match {
  //      case 10f =>
  //        List(
  //          s"${tileInfoPath}/B02.jp2",
  //          s"${tileInfoPath}/B03.jp2",
  //          s"${tileInfoPath}/B04.jp2",
  //          s"${tileInfoPath}/B08.jp2"
  //        )
  //      case 20f =>
  //        List(
  //          s"${tileInfoPath}/B05.jp2",
  //          s"${tileInfoPath}/B06.jp2",
  //          s"${tileInfoPath}/B07.jp2",
  //          s"${tileInfoPath}/B8A.jp2",
  //          s"${tileInfoPath}/B11.jp2",
  //          s"${tileInfoPath}/B12.jp2"
  //        )
  //      case 60f =>
  //        List(
  //          s"${tileInfoPath}/B01.jp2",
  //          s"${tileInfoPath}/B09.jp2",
  //          s"${tileInfoPath}/B10.jp2"
  //        )
  //      case _ =>
  //        throw new InvalidParameterException(s"Unable to create images for $tileInfoPath at resolution $resolution")
  //    }
  //
  //    keys.map { obj =>
  //      val filename = obj.split("/").last
  //      val imageBand = filename.split("\\.").head
  //      val objMetadata = s3Client.client.getObjectMetadata(sentinel2Config.bucketName, obj)
  //      Image.Banded(
  //        organizationId   = sentinel2Config.organizationUUID,
  //        rawDataBytes     = objMetadata.getContentLength.toInt,
  //        visibility       = Visibility.Public,
  //        filename         = filename,
  //        sourceUri        = s"${sentinel2Config.baseHttpPath}${obj}",
  //        owner            = systemUser.some,
  //        scene            = sceneId,
  //        imageMetadata    = Json.Null,
  //        resolutionMeters = resolution,
  //        metadataFiles    = List(),
  //        bands            = Seq(sentinel2Config.bandByName(imageBand)).flatten
  //      )
  //    }
  //  }
  //
  //  def createThumbnails(sceneId: UUID, tilePath: String): List[Thumbnail.Identified] = {
  //    val keyPath: String = s"$tilePath/preview.jpg"
  //    val thumbnailUrl = s"${sentinel2Config.baseHttpPath}$keyPath"
  //
  //    if (!isUriExists(thumbnailUrl)) Nil
  //    else
  //      Thumbnail.Identified(
  //        id = None,
  //        organizationId = UUID.fromString(landsat8Config.organization),
  //        thumbnailSize  = ThumbnailSize.Square,
  //        widthPx        = 343,
  //        heightPx       = 343,
  //        sceneId        = sceneId,
  //        url            = thumbnailUrl
  //      ) :: Nil
  //  }
  //
  //  def getSentinel2Products(date: LocalDate): List[URI] = {
  //    s3Client
  //      .listKeys(
  //        s3bucket  = sentinel2Config.bucketName,
  //        s3prefix  = s"products/${date.getYear}/${date.getMonthValue}/${date.getDayOfMonth}/",
  //        ext       = "productInfo.json",
  //        recursive = true
  //      ).toList
  //  }
  //
  //  def findScenes(date: LocalDate, keys: List[URI], user: User, datasources: Map[String, String]): Future[List[Option[Scene]]] = {
  //    val datasourceIds = datasources map { case (p, i) => UUID.fromString(i) }
  //    Scenes.getDatasourcesScenesForDay(date, datasourceIds.toSeq).map { existingScenes =>
  //      Future.sequence {
  //        keys.map { uri =>
  //          Future {
  //            val optList = for {
  //              json <- s3Client.getObject(uri.getHost, uri.getPath.tail).getObjectContent.toJson
  //              tilesJson <- json.hcursor.downField("tiles").as[List[Json]].toOption
  //            } yield {
  //              tilesJson.flatMap { tileJson =>
  //                val sceneResult = Try {
  //                  tileJson.hcursor.downField("path").as[String].toOption.map { tilePath =>
  //                    val sceneId = UUID.randomUUID()
  //                    val sceneName = s"S2 $tilePath"
  //
  //                    if (existingScenes.contains(sceneName)) {
  //                      logger.info(s"Skipping scene creation. Scene already exists: ${tilePath} - ${sceneName}")
  //                      Future(None)
  //                    } else {
  //                      logger.info(s"Starting scene creation: ${tilePath}- ${sceneName}")
  //                      val tileinfo =
  //                        s3Client
  //                          .getObject(sentinel2Config.bucketName, s"${tilePath}/tileInfo.json")
  //                          .getObjectContent
  //                          .toJson
  //                          .getOrElse(Json.Null)
  //
  //                      logger.info(s"Getting scene metadata for ${tilePath}")
  //                      val sceneMetadata: Map[String, String] = getSceneMetadata(tileinfo)
  //
  //                      val datasource =
  //                        datasources
  //                          .toList
  //                          .find(d => sceneMetadata("productName").startsWith(d._1))
  //                          .getOrElse(throw new Exception("Unknown datasource"))
  //
  //                      val datasourcePrefix = datasource._1
  //                      val datasourceId = datasource._2
  //
  //                      logger.info(s"${datasourcePrefix} - Creating images for sentinel 2 scene: ${tilePath}")
  //                      val images = List(10f, 20f, 60f).map(createImages(sceneId, tileinfo, _)).reduce(_ ++ _)
  //
  //                      logger.info(s"${datasourcePrefix} - Extracting polygons for ${tilePath}")
  //                      val tileFootprint = multiPolygonFromJson(tileinfo, "tileGeometry", sentinel2Config.targetProjCRS)
  //                      val dataFootprint = multiPolygonFromJson(tileinfo, "tileDataGeometry", sentinel2Config.targetProjCRS)
  //
  //                      val awsBase = s"https://${sentinel2Config.bucketName}.s3.amazonaws.com"
  //
  //                      val metadataFiles = List(
  //                        s"$awsBase/$tilePath/tileInfo.json",
  //                        s"$awsBase/$tilePath/metadata.xml",
  //                        s"$awsBase/$tilePath/productInfo.json"
  //                      )
  //
  //                      val cloudCover = sceneMetadata.get("dataCoveragePercentage").map(_.toFloat)
  //                      val acquisitionDate = sceneMetadata.get("timeStamp").map { dt =>
  //                        new java.sql.Timestamp(
  //                          ZonedDateTime
  //                            .parse(dt)
  //                            .toInstant
  //                            .getEpochSecond * 1000l
  //                        )
  //                      }
  //
  //                      logger.info(s"${datasourcePrefix} - Creating scene case class ${tilePath}")
  //                      val scene = Scene.Create(
  //                        id = sceneId.some,
  //                        organizationId = sentinel2Config.organizationUUID,
  //                        ingestSizeBytes = 0,
  //                        visibility = Visibility.Public,
  //                        tags = List("Sentinel-2", "JPEG2000"),
  //                        datasource = UUID.fromString(datasourceId),
  //                        sceneMetadata = sceneMetadata.asJson,
  //                        name = sceneName,
  //                        owner = systemUser.some,
  //                        tileFootprint = (sentinel2Config.targetProjCRS.epsgCode |@| tileFootprint).map {
  //                          case (code, mp) => Projected(mp, code)
  //                        },
  //                        dataFootprint = (sentinel2Config.targetProjCRS.epsgCode |@| dataFootprint).map {
  //                          case (code, mp) => Projected(mp, code)
  //                        },
  //                        metadataFiles = metadataFiles,
  //                        images = images,
  //                        thumbnails = createThumbnails(sceneId, tilePath),
  //                        ingestLocation = None,
  //                        filterFields = SceneFilterFields(
  //                          cloudCover = cloudCover,
  //                          acquisitionDate = acquisitionDate
  //                        ),
  //                        statusFields = SceneStatusFields(
  //                          thumbnailStatus = JobStatus.Success,
  //                          boundaryStatus = JobStatus.Success,
  //                          ingestStatus = IngestStatus.NotIngested
  //                        )
  //                      )
  //
  //                      logger.info(s"${datasourcePrefix} - Importing scene $sceneId...")
  //
  //                      val future =
  //                        Scenes.insertScene(scene, user).map(_.toScene).recover {
  //                          case e: PSQLException => {
  //                            logger.error(s"${datasourcePrefix} - An error occurred during scene $sceneId import. Skipping...")
  //                            logger.error(e.stackTraceString)
  //                            sendError(e)
  //                            scene.toScene(user)
  //                          }
  //                          case e => {
  //                            logger.error(s"${datasourcePrefix} - An unknown error occurred during scene import")
  //                            logger.error(e.stackTraceString)
  //                            sendError(e)
  //                            scene.toScene(user)
  //                          }
  //                        }
  //
  //                      future onComplete {
  //                        case Success(s) => logger.info(s"${datasourcePrefix} - Finished importing scene.")
  //                        case Failure(e) => {
  //                          logger.error(s"${datasourcePrefix} - An error occurred during scene $sceneId import.")
  //                          logger.error(e.stackTraceString)
  //                          sendError(e)
  //                        }
  //                      }
  //                      future.map(Some(_))
  //                    }
  //                  }
  //                }
  //                sceneResult.recoverWith {
  //                  case e => {
  //                    logger.error(s"An error occurred during scene import: ${uri.getPath.tail}")
  //                    logger.error(e.stackTraceString)
  //                    sendError(e)
  //                    Failure(e)
  //                  }
  //                }.toOption.flatMap(identity _)
  //              }
  //            }
  //            Future.sequence(optList.getOrElse(Nil))
  //          }.flatMap(identity _)
  //        }
  //      }.map(_.flatten)
  //    }
  //  }.flatten

  def run: Unit = {
    //    logger.info("Importing scenes...")
    //    Users.getUserById(systemUser).flatMap { userOpt =>
    //      logger.info(s"Getting scenes for $startDate")
    //      val keys = getSentinel2Products(startDate)
    //      findScenes(startDate, keys, userOpt.getOrElse {
    //        val e = new Exception(s"User $systemUser doesn't exist.")
    //        sendError(e)
    //        throw e
    //      }, sentinel2Config.datasourceIds)
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
//
//    val job = args.toList match {
//      case List(date) => ImportSentinel2(LocalDate.parse(date))
//      case _ => ImportSentinel2()
//    }
//
//    job.run
  }
}
