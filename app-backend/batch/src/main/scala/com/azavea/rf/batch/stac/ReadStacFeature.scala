package com.azavea.rf.batch.stac

import java.net.URI
import java.sql.Timestamp
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.batch.util._
import com.azavea.rf.batch.util.conf.Config
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.database.{SceneDao, UserDao}
import com.azavea.rf.datamodel.{stac, _}
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.proj4.CRS
import io.circe.generic.JsonCodec
import io.circe.parser._
import io.circe.syntax._
import geotrellis.vector._
import javax.imageio.ImageIO

import scala.io.Source
import scala.util._

@JsonCodec
final case class MetadataWithStartStop(start: Timestamp, end: Timestamp)

object CommandLine {

  final case class Params(
      path: String = "",
      testRun: Boolean = false,
      // Default will never be used because parameter is required
      datasource: UUID = UUID.randomUUID()
  )

  val parser =
    new scopt.OptionParser[Params]("raster-foundry-stac-conversion") {
      // for debugging; prevents exit from sbt console
      override def terminate(exitState: Either[String, Unit]): Unit = ()

      head("raster-foundry-stac-conversion", "0.1")

      opt[Unit]('t', "test")
        .action(
          (_, conf) => conf.copy(testRun = true)
        )
        .text("Dry run stac conversion to scene to verify output")

      opt[String]('p', "path")
        .required()
        .action(
          (p, conf) => conf.copy(path = p)
        )
        .text("STAC geojson URI. ex: 'file:///opt/raster-foundry/app-backend/stac.geojson' 's3://{uri}' 'http://{uri}'")

      opt[String]('d', "datasource")
        .required()
        .action(
          (d, conf) => {
            conf.copy(datasource = UUID.fromString(d))
          }
        )
        .text("Datasource to create scene with")
    }
}

object ReadStacFeature extends Config with LazyLogging {
  val name = "read_stac_feature"
  implicit val xa = RFTransactor.xa

  def main(args: Array[String]): Unit = {
    val params = CommandLine.parser.parse(args, CommandLine.Params()) match {
      case Some(params) =>
        params
      case None =>
        return
    }
    val path = params.path
    val rootUri = new URI(
      path.split("/").iterator.sliding(2).flatMap(_.headOption).mkString("/"))
    val geojson =
      Source.fromInputStream(getStream(new URI(path))).getLines.mkString
    val decoded = decode[stac.Feature](geojson)
    decoded match {
      case Right(stacFeature) =>
        val scene = stacFeatureToScene(stacFeature, params.datasource, rootUri)
        params.testRun match {
          case true =>
            logger.info(
              s"Test run, so scene was not actually created:\n${scene}")
          case _ =>
            writeSceneToDb(scene)
        }
      case Left(error) =>
        logger.error(
          s"There was an error decoding the geojson into a stac Feature: ${error.getLocalizedMessage}")
    }
  }

  protected def stacFeatureToScene(
      feature: stac.Feature,
      datasource: UUID,
      rootUri: URI
  ): Scene.Create = {
    val thumbnailLinks = feature.links.filter(_.`type` == "thumbnail")

    // if datasource is defined, use bands from datasource
    val (imageAssets, metadataFiles) =
      feature.assets.partition(asset => asset.format.getOrElse("none") == "cog")

    val sceneId = UUID.randomUUID()
    val images = getBandedImages(imageAssets, sceneId, rootUri) // get bands from the image products

    val srid = feature.geometry.srid

    val geom = feature.geometry.geom
      .reproject(CRS.fromEpsgCode(srid), CRS.fromEpsgCode(3857))
    val dataFootprint = geom
      .as[Polygon]
      .map(g => Projected(MultiPolygon(g), 3857))
      .orElse(geom.as[MultiPolygon].map(g => Projected(g, 3857)))

    Scene.Create(
      id = Some(sceneId),
      visibility = Visibility.Public,
      tags = feature.properties.provider.split(",").map(_.trim).toList,
      datasource = datasource,
      sceneMetadata = MetadataWithStartStop(feature.properties.start,
                                            feature.properties.end).asJson,
      name = feature.id,
      owner = Some(systemUser),
      tileFootprint = Some(multipolygonFromBbox(feature.bbox)),
      dataFootprint = dataFootprint,
      metadataFiles = metadataFiles.map(asset => asset.href).toList, // assets that are not the primary image
      images = images,
      thumbnails =
        thumbnailLinks.flatMap(thumbnailFromLink(_, sceneId, rootUri)).toList,
      ingestLocation = None,
      filterFields = SceneFilterFields(
        cloudCover = Some(0), // required for search in the frontend
        sunAzimuth = None,
        sunElevation = None,
        acquisitionDate = Some(feature.properties.start)
      ),
      statusFields = SceneStatusFields(
        thumbnailStatus = thumbnailLinks.size match {
          case 0 => JobStatus.Queued // should kick off thumbnail creation
          case _ => JobStatus.Success
        },
        boundaryStatus = JobStatus.Success, // tile and data footprints are required fields
        ingestStatus = IngestStatus.NotIngested
      ),
      sceneType = Some(SceneType.Avro)
    )
  }

  protected def writeSceneToDb(scene: Scene.Create)(
      implicit xa: Transactor[IO]): Scene.WithRelated = {
    val sceneInsertIO: ConnectionIO[Scene.WithRelated] =
      UserDao.getUserById(systemUser) flatMap {
        case Some(user) =>
          logger.info(s"\nuser: ${user.id}\ninserting scene: \n${scene.name}")
          SceneDao.insert(scene, user)
        case _ =>
          throw new RuntimeException(
            "System user not found. Make sure migrations have been run, and that batch config is correct")
      }
    sceneInsertIO.transact(xa).unsafeRunSync
  }

  protected def getBandedImages(imageAssets: Seq[stac.Asset],
                                sceneId: UUID,
                                rootUri: URI): List[Image.Banded] = {
    // get product
    imageAssets
      .flatMap(imageAsset =>
        imageAsset.product match {
          case Some(href) =>
            // get product from file
            val productJson = Source
              .fromInputStream(getStream(new URI(href), rootUri))
              .getLines
              .mkString
            val decoded = decode[stac.Product](productJson)
            decoded match {
              case Right(stacProduct) =>
                Some(createImage(stacProduct, imageAsset, sceneId, rootUri))
              case Left(error) =>
                logger.error(
                  s"There was an error decoding json into a stac Product: ${error.getLocalizedMessage}")
                None
            }
          case _ => None
      })
      .toList
  }

  protected def createImage(
      product: stac.Product,
      imageAsset: stac.Asset,
      sceneId: UUID,
      rootUri: URI
  ): Image.Banded = {
    Image.Banded(
      rawDataBytes = 0, // sizeFromPath(params.path),
      visibility = Visibility.Public,
      filename = imageAsset.href.split("/").takeRight(1)(0),
      sourceUri = combineUris(new URI(imageAsset.href), rootUri).toString,
      owner = Some(systemUser),
      scene = sceneId,
      imageMetadata = product.properties,
      resolutionMeters = product.bands.map(_.gsd).min,
      metadataFiles = List(),
      bands = product.bands.map(
        band =>
          Band.Create(
            name = band.commonName,
            number = band.imageBandIndex,
            wavelength = List(band.centerWavelength.toInt)
        )
      )
    )
  }

  protected def multipolygonFromBbox(
      bbox: Seq[Double]): Projected[MultiPolygon] = {
    val topLeft = Point(bbox(0), bbox(1))
    val topRight = Point(bbox(2), bbox(1))
    val lowerRight = Point(bbox(2), bbox(3))
    val lowerLeft = Point(bbox(0), bbox(3))
    val poly = MultiPolygon(
      Polygon(topLeft, topRight, lowerRight, lowerLeft, topLeft)
    ).reproject(CRS.fromEpsgCode(4326), CRS.fromEpsgCode(3857))
    Projected(poly, 3857)
  }

  // PNGs for stac features must be referenced at radiant-nasa-iserv.s3.amazonaws.com/... instead of
  // at s3.amazonaws.com/bucket/...
  private def createThumbnailUrl(thumbnailPath: URI, rootUri: URI): URI = {
    val base = new URI(
      rootUri.getScheme match {
        case "s3"             => s"https://${rootUri.getHost}.s3.amazonaws.com"
        case "http" | "https" => s"https://${rootUri.getHost}"
        case _                => ""
      }
    )
    val path =
      combineUris(thumbnailPath, new URI(rootUri.getPath.dropWhile(_ == '/')))
    combineUris(path, base)
  }

  @SuppressWarnings(Array("CatchException"))
  protected def thumbnailFromLink(
      link: stac.Link,
      sceneId: UUID,
      rootUri: URI): Option[Thumbnail.Identified] = {
    // fetch thumbnail, get width/height
    try {
      val thumb = ImageIO.read(getStream(new URI(link.href), rootUri))
      // create thumbnail
      val width = thumb.getWidth
      val height = thumb.getHeight
      Some(
        Thumbnail.Identified(
          id = None,
          thumbnailSize =
            if (width < 500) ThumbnailSize.Small else ThumbnailSize.Large,
          widthPx = thumb.getWidth,
          heightPx = thumb.getHeight,
          sceneId = sceneId,
          url = createThumbnailUrl(new URI(link.href), rootUri).toString
        ))
    } catch {
      case e: Exception =>
        logger.error(
          s"Error fetching thumbnail with URI: ${link.href}, ${rootUri}")
        None
    }
  }
}
