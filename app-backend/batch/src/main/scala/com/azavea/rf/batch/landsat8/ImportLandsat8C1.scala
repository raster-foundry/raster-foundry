package com.azavea.rf.batch.landsat8

import java.io.File
import java.time.{LocalDate, ZoneOffset}
import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.azavea.rf.batch.Job
import com.azavea.rf.batch.util.{S3, isUriExists}
import com.azavea.rf.common.utils.AntimeridianUtils
import com.azavea.rf.database._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel._
import com.github.tototoshi.csv._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import geotrellis.proj4.CRS
import io.circe._
import io.circe.syntax._
import geotrellis.vector._

import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParSeq
import scala.concurrent.forkjoin.ForkJoinPool
import scala.sys.process._

final case class ImportLandsat8C1(
    startDate: LocalDate = LocalDate.now(ZoneOffset.UTC),
    threshold: Int = 10)(implicit val xa: Transactor[IO])
    extends Job {
  val name = ImportLandsat8C1.name

  /** Get S3 client per each call */
  def s3Client = S3(region = landsat8Config.awsRegion)

  // Eagerly get existing scene names
  val previousDay = startDate.minusDays(1)
  val nextDay = startDate.plusDays(1)
  val sceneQuery =
    sql"""SELECT name
            FROM scenes
            WHERE
            acquisition_date <= ${nextDay.toString}::timestamp AND
            acquisition_date >= ${previousDay.toString}::timestamp AND
            datasource = ${landsat8Config.datasourceUUID}::uuid
      """.query[String].to[List]
  val existingSceneNames = sceneQuery.transact(xa).unsafeRunSync.toSet

  protected def rowsFromCsv: List[Map[String, String]] = {
    logger.info("Downloading and filtering Landsat CSV")
    /* This preprocessing downloads the file to stdout, unzips, and filters to the
     * header row or rows containing the start date in their acquisitionDate column,
     * I _think_ in a single traversal of the file. Without a very high download speed
     * (ethernet is slower than wifi in the office, for some reason), it takes about 90
     * total seconds to do everything.
     *
     * Also, java's ProcessBuilder does some weird things to quotes, so while it's necessary
     * in real life to single-quote the awk script, it's necessary not to here. It's weird
     * and I don't like it but no quotes/no spaces was the only way I could get it to
     * interpret the awk script correctly.
     */
    s"wget -q -O - ${landsat8Config.usgsLandsatUrlC1}.gz" #|
      "zcat" #|
      s"""awk -F, {if($$33=="${startDate}"||/acquisitionDate/){print}}""" #>
      new File("/tmp/landsat.csv") !

    logger.info("CSV downloaded and filtered successfully")

    val reader = CSVReader.open(new File("/tmp/landsat.csv"))
    val rows = reader.allWithHeaders()
    logger.info(s"Found ${rows.length} rows for ${startDate}")
    rows
  }

  protected def getLandsatPath(productId: String): String = {
    val p = productId.split("_")(2)
    val (wPath, wRow) = p.substring(0, 3) -> p.substring(3, 6)
    val path = s"L8/$wPath/$wRow/$productId"
    logger.debug(s"Constructed path: $path")
    path
  }

  protected def getLandsatUrl(productId: String): String = {
    val rootUrl =
      s"${landsat8Config.awsLandsatBaseC1}${getLandsatPath(productId)}"
    logger.debug(s"Constructed Root URL: $rootUrl")
    rootUrl
  }

  // Getting the image size is the only place where the s3 object
  // is required to exist -- so handle the missing object by returning
  // a -1 for the image's size
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

  @SuppressWarnings(Array("TraversableHead"))
  protected def csvRowToScene(row: Map[String, String],
                              user: User,
                              srcProj: CRS = CRS.fromName("EPSG:4326"),
                              targetProj: CRS = CRS.fromName("EPSG:3857"))(
      implicit xa: Transactor[IO]): IO[Option[Scene.WithRelated]] = {

    val sceneId = UUID.randomUUID()
    val productId = row("LANDSAT_PRODUCT_ID")
    val landsatPath = getLandsatPath(productId)

    val maybeInsertScene: ConnectionIO[Option[Scene.WithRelated]] = for {
      sceneInsert <- {

        val sceneNameExists = existingSceneNames.contains(landsatPath)
        sceneNameExists match {
          case true => {
            logger.warn(s"Skipping inserting ${landsatPath} -- already exists")
            None.pure[ConnectionIO]
          }
          case _ => {
            createSceneFromRow(row,
                               srcProj,
                               targetProj,
                               sceneId,
                               productId,
                               landsatPath) match {
              case Some(scene) => SceneDao.insertMaybe(scene, user)
              case _           => None.pure[ConnectionIO]
            }
          }
        }
      }
    } yield {
      sceneInsert
    }
    maybeInsertScene.transact(xa)
  }

  // All of the heads here are from a locally constructed list that we know has members
  @SuppressWarnings(Array("TraversableHead"))
  private def createSceneFromRow(row: Map[String, String],
                                 srcProj: CRS,
                                 targetProj: CRS,
                                 sceneId: UUID,
                                 productId: String,
                                 landsatPath: String) = {

    val (tileFootprint, dataFootprint) =
      getRowFootprints(row, srcProj, targetProj)

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

      /** Some Landsat 8 scenes have negative cloud cover, indicating that they're bad
        * or non-imagery data in some way. We don't know what they are. But we dislike them
        * and never want to see them again
        */
      if (cloudCover.map(_ < 0).getOrElse(false)) {
        logger.info(
          s"Skipping import for ${landsatPath} since cloud_cover was ${cloudCover}")
        None
      } else {
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
              rawDataBytes = 0,
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
          visibility = Visibility.Public,
          tags = tags,
          datasource = landsat8Config.datasourceUUID,
          sceneMetadata = sceneMetadata.asJson,
          name = landsatPath,
          owner = Some(systemUser),
          tileFootprint = tileFootprint,
          dataFootprint = dataFootprint,
          metadataFiles = List(
            s"${landsat8Config.awsLandsatBaseC1}${landsatPath}/${productId}_MTL.txt"),
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
          ),
          sceneType = Some(SceneType.Avro)
        )
        Some(scene)
      }
    }
  }

  @SuppressWarnings(Array("ListAppend", "TraversableHead", "TraversableLast"))
  def getRowFootprints(row: Map[String, String], srcProj: CRS, targetProj: CRS)
    : (Option[Projected[MultiPolygon]], Option[Projected[MultiPolygon]]) = {
    val ll = row("lowerLeftCornerLongitude").toDouble -> row(
      "lowerLeftCornerLatitude").toDouble
    val lr = row("lowerRightCornerLongitude").toDouble -> row(
      "lowerRightCornerLatitude").toDouble
    val ul = row("upperLeftCornerLongitude").toDouble -> row(
      "upperLeftCornerLatitude").toDouble
    val ur = row("upperRightCornerLongitude").toDouble -> row(
      "upperRightCornerLatitude").toDouble

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
      else
        srcPolygon.reproject(srcProj, targetProj) -> extent.reproject(
          srcProj,
          targetProj)
    }

    val dataFootprint = MultiPolygon(transformedCoords)
    val tileFootprint = MultiPolygon(transformedExtent.toPolygon())

    val intersects = AntimeridianUtils.crossesAntimeridian(tileFootprint)

    val correctedDataFootprint = AntimeridianUtils.correctDataFootprint(
      intersects,
      Some(dataFootprint),
      targetProj)
    val correctedTileFootprint = AntimeridianUtils.correctTileFootprint(
      intersects,
      Some(tileFootprint),
      targetProj)

    (correctedTileFootprint, correctedDataFootprint)
  }

  def run(): Unit = {
    logger.info("Importing scenes...")

    val user = UserDao.unsafeGetUserById(systemUser).transact(xa).unsafeRunSync
    val rows = rowsFromCsv.par
    rows.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(16))
    val insertedScenes: ParSeq[Option[Scene.WithRelated]] = rows map {
      (row: Map[String, String]) =>
        {
          csvRowToScene(row, user)
            .handleErrorWith(
              (error: Throwable) => {
                sendError(error)
                IO.pure(None)
              }
            )
            .unsafeRunSync
        }
    }
    stop
  }
}

object ImportLandsat8C1 {
  val name = "import_landsat8_c1"

  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa

    val job = args.toList match {
      case List(date, threshold) =>
        ImportLandsat8C1(LocalDate.parse(date), threshold.toInt)
      case List(date) => ImportLandsat8C1(LocalDate.parse(date))
      case _          => ImportLandsat8C1()
    }

    job.run
  }
}
