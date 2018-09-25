package com.azavea.rf.batch.landsat8

import java.time.{LocalDate, ZoneOffset}
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util._
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel._
import doobie.util.transactor.Transactor
import jp.ne.opt.chronoscala.Imports._

import scala.util.{Failure, Success, Try}

@deprecated(
  message =
    "Use com.azavea.rf.batch.landsat8.airflow.ImportLandsat8C1 instead, as USGS changes LC8 storage rules: https://landsat.usgs.gov/landsat-collections",
  since = "https://github.com/azavea/raster-foundry/pull/1821"
)
final case class ImportLandsat8(
    startDate: LocalDate = LocalDate.now(ZoneOffset.UTC),
    threshold: Int = 10)(implicit val xa: Transactor[IO])
    extends Job {
  val name = ImportLandsat8.name

  /** Get S3 client per each call */
  def s3Client = S3(region = landsat8Config.awsRegion)

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

object ImportLandsat8 {
  val name = "import_landsat8"

  implicit val xa = RFTransactor.xa

  def main(args: Array[String]): Unit = {

    /** Since 30/04/2017 old LC8 collection is not updated */
    val job = args.toList match {
      case List(date, threshold)
          if LocalDate.parse(date) > LocalDate.of(2017, 4, 30) =>
        ImportLandsat8C1(LocalDate.parse(date), threshold.toInt)
      case List(date) if LocalDate.parse(date) > LocalDate.of(2017, 4, 30) =>
        ImportLandsat8C1(LocalDate.parse(date))
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

    job.run
  }
}
