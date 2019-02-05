package com.rasterfoundry.batch.landsat8

import com.rasterfoundry.batch.Job
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.common.{S3, S3RegionString}
import com.rasterfoundry.common.datamodel._

import cats.effect.IO
import doobie.util.transactor.Transactor
import jp.ne.opt.chronoscala.Imports._

import scala.util.{Failure, Success, Try}
import java.time.{LocalDate, ZoneOffset}
import java.util.UUID

@deprecated(
  message =
    "Use com.rasterfoundry.batch.landsat8.airflow.ImportLandsat8C1 instead, as USGS changes LC8 storage rules: https://landsat.usgs.gov/landsat-collections",
  since = "https://github.com/azavea/raster-foundry/pull/1821"
)
final case class ImportLandsat8(
    startDate: LocalDate = LocalDate.now(ZoneOffset.UTC),
    threshold: Int = 10)(implicit val xa: Transactor[IO])
    extends RollbarNotifier
    with Config {
  val name = ImportLandsat8.name

  def runJob(args: List[String]) = ???

  /** Get S3 client per each call */
  def s3Client =
    S3(region = landsat8Config.awsRegion.flatMap { region =>
      Some(S3RegionString(region))
    })

  protected def getLandsatPath(productId: String): String = {
    val (wPath, wRow) = productId.substring(3, 6) -> productId.substring(6, 9)
    val path = s"L8/$wPath/$wRow/$productId"
    logger.debug(s"Constructed path: $path")
    path
  }

  protected def getLandsatUrl(productId: String): String = {
    val rootUrl =
      s"${landsat8Config.awsLandsatBase}${getLandsatPath(productId)}"
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
      s3Client
        .getObject(landsat8Config.bucketName, path)
        .getObjectMetadata
        .getContentLength
        .toInt
    ) match {
      case Success(size) => size
      case Failure(_)    => -1
    }
  }

  protected def createThumbnails(
      sceneId: UUID,
      productId: String): List[Thumbnail.Identified] = {
    val path = getLandsatUrl(productId)
    val smallUrl = s"$path/${productId}_thumb_small.jpg"
    val largeUrl = s"$path/${productId}_thumb_large.jpg"

    Thumbnail.Identified(
      id = None,
      thumbnailSize = ThumbnailSize.Small,
      widthPx = 228,
      heightPx = 233,
      sceneId = sceneId,
      url = smallUrl
    ) :: Thumbnail.Identified(
      id = None,
      thumbnailSize = ThumbnailSize.Large,
      widthPx = 1143,
      heightPx = 1168,
      sceneId = sceneId,
      url = largeUrl
    ) :: Nil
  }

  def run(): Unit = ???
}

object ImportLandsat8 extends Job {
  val name = "import_landsat8"

  def runJob(args: List[String]): IO[Unit] = {

    /** Since 30/04/2017 old LC8 collection is not updated */
    args match {
      case argList @ List(date, threshold)
          if LocalDate.parse(date) > LocalDate.of(2017, 4, 30) =>
        ImportLandsat8C1.runJob(argList)
      case argList @ List(date)
          if LocalDate.parse(date) > LocalDate.of(2017, 4, 30) =>
        ImportLandsat8C1.runJob(argList)
      case List(date, threshold) =>
        throw new NotImplementedError(
          "Landsat 8 import for dates prior to May 1, 2017 is not implemented"
        )
      case List(date) =>
        throw new NotImplementedError(
          "Landsat 8 import for dates prior to May 1, 2017 is not implemented"
        )
      case _ =>
        throw new NotImplementedError(
          "Landsat 8 import for dates prior to May 1, 2017 is not implemented"
        )
    }
  }
}
