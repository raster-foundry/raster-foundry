package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.{Scene, SceneFilterFields, SceneStatusFields, User, Visibility}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

import com.azavea.rf.database.util.Page
import com.lonelyplanet.akka.http.extensions.PageRequest
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, MultiPolygon, Polygon}
import io.circe.{Decoder, Encoder, Json}

import scala.concurrent.duration._
import java.sql.Timestamp
import java.util.Date

import geotrellis.proj4.LatLng
import geotrellis.raster.io.geotiff.reader.GeoTiffReader

import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, Point, Polygon}
import geotrellis.util.{ FileRangeReader, RangeReader }
import geotrellis.spark.io.s3.util.S3RangeReader
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.spark.io.http.util.HttpRangeReader

import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.{AmazonS3URI, AmazonS3Client => AWSAmazonS3Client}
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain

import scala.util.Try
import java.net.URI
import java.net.URL


object RangeReaderUtils extends LazyLogging {
  def fromUri(uri: String): Option[RangeReader] = {
    import java.nio.file._

    val javaUri = new URI(uri)
    javaUri.getScheme match {
      case "file" | null =>
        Some(FileRangeReader(Paths.get(javaUri).toFile))

      case "http" | "https" =>
        Some(HttpRangeReader(new URL(uri)))

      case "s3" =>
        val s3Uri = new AmazonS3URI(java.net.URLDecoder.decode(uri, "UTF-8"))
        val s3Client = new AmazonS3Client(new AWSAmazonS3Client(new DefaultAWSCredentialsProviderChain))
        Some(S3RangeReader(s3Uri.getBucket, s3Uri.getKey, s3Client))

      case scheme =>
        None
    }
  }

}

object SceneDao extends Dao[Scene] {

  type KickoffIngest = Boolean

  val tableName = "scenes"

  import RangeReaderUtils._

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, ingest_size_bytes, visibility, tags,
      datasource, scene_metadata, name, tile_footprint,
      data_footprint, metadata_files, ingest_location, cloud_cover,
      acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
      boundary_status, ingest_status, scene_type
    FROM
  """ ++ tableF

  def getSceneById(id: UUID, user: User): ConnectionIO[Option[Scene]] =
    query.filter(id).ownerFilter(user).selectOption

  def unsafeGetSceneById(id: UUID, user: User): ConnectionIO[Scene] =
    query.filter(id).ownerFilter(user).select

  def insert(sceneCreate: Scene.Create, user: User): ConnectionIO[Scene.WithRelated] = {
    val scene = sceneCreate.toScene(user)
    val thumbnails = sceneCreate.thumbnails.map(_.toThumbnail(user.id))
    val images = (sceneCreate.images map { im: Image.Banded => im.toImage(user) }).zipWithIndex
    val bands = images map { case (im: Image, ind: Int) =>
      sceneCreate.images(ind).bands map { bd => bd.toBand(im.id) }
    }

    def getTiffExtent(uri: String): Option[Projected[MultiPolygon]] = {
      for {
        rr <- fromUri(uri)
        tiff = GeoTiffReader.readMultiband(rr, decompress = false, streaming = true)
      } yield {
        val crs = tiff.crs
        Projected(MultiPolygon(tiff.extent.reproject(crs, WebMercator).toPolygon()), 3857)
      }
    }

    def getTiffHistogram(uri: String) = {
      for {
        rr <- fromUri(uri)
        tiff = GeoTiffReader.readMultiband(rr, decompress = false, streaming = true)
      } yield {
        tiff.tile.histogram
      }
    }

    val tileFootprint = (scene.sceneType, scene.ingestLocation) match {
      case (Some(SceneType.COG), Some(ingestLocation)) => {
        val e = getTiffExtent(ingestLocation)
        println(s"GOT IT ${e}")
        e
      }
      case _ => {
        println("NOT A COG")
        None
      }
    }

    val sceneInsertId = (fr"INSERT INTO" ++ tableF ++ fr"""(
         id, created_at, created_by, modified_at, modified_by, owner,
         organization_id, ingest_size_bytes, visibility, tags,
         datasource, scene_metadata, name, tile_footprint,
         data_footprint, metadata_files, ingest_location, cloud_cover,
         acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
         boundary_status, ingest_status, scene_type
      )""" ++ fr"""VALUES (
        ${scene.id}, ${scene.createdAt}, ${scene.createdBy}, ${scene.modifiedAt}, ${scene.modifiedBy}, ${scene.owner},
        ${scene.organizationId}, ${scene.ingestSizeBytes}, ${scene.visibility}, ${scene.tags},
        ${scene.datasource}, ${scene.sceneMetadata}, ${scene.name}, ${tileFootprint},
        ${scene.dataFootprint}, ${scene.metadataFiles}, ${scene.ingestLocation}, ${scene.filterFields.cloudCover},
        ${scene.filterFields.acquisitionDate}, ${scene.filterFields.sunAzimuth}, ${scene.filterFields.sunElevation},
        ${scene.statusFields.thumbnailStatus}, ${scene.statusFields.boundaryStatus},
        ${scene.statusFields.ingestStatus}, ${scene.sceneType.getOrElse(SceneType.Avro)}
      )
    """).update.withUniqueGeneratedKeys[UUID]("id")

    val thumbnailInsert = ThumbnailDao.insertMany(thumbnails)
    val imageInsert = ImageDao.insertManyImages(images.map(_._1))
    val bandInsert = BandDao.createMany(bands.flatten)
    val sceneWithRelatedquery = SceneWithRelatedDao.query.filter(scene.id).select

    for {
      sceneId <- sceneInsertId
      _ <- thumbnailInsert
      _ <- imageInsert
      _ <- bandInsert
      // It's fine to do this unsafely, since we know we the prior insert succeeded
      sceneWithRelated <- SceneWithRelatedDao.unsafeGetScene(sceneId, user)
    } yield sceneWithRelated
  }

  def insertMaybe(sceneCreate: Scene.Create, user: User): ConnectionIO[Option[Scene.WithRelated]] = {
    val scene = sceneCreate.toScene(user)
    val thumbnails = sceneCreate.thumbnails.map(_.toThumbnail(user.id))
    val images = (sceneCreate.images map { im: Image.Banded => im.toImage(user) }).zipWithIndex
    val bands = images map { case (im: Image, ind: Int) =>
      sceneCreate.images(ind).bands map { bd => bd.toBand(im.id) }
    }

    val sceneInsert = (Fragment.const(s"""
      INSERT INTO ${tableName} (
         id, created_at, created_by, modified_at, modified_by, owner,
         organization_id, ingest_size_bytes, visibility, tags,
         datasource, scene_metadata, name, tile_footprint,
         data_footprint, metadata_files, ingest_location, cloud_cover,
         acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
         boundary_status, ingest_status, scene_type
      )""") ++ fr"""VALUES (
        ${scene.id}, ${scene.createdAt}, ${scene.createdBy}, ${scene.modifiedAt}, ${scene.modifiedBy}, ${scene.owner},
        ${scene.organizationId}, ${scene.ingestSizeBytes}, ${scene.visibility}, ${scene.tags},
         ${scene.datasource}, ${scene.sceneMetadata}, ${scene.name}, ${scene.tileFootprint},
        ${scene.dataFootprint}, ${scene.metadataFiles}, ${scene.ingestLocation}, ${scene.filterFields.cloudCover},
        ${scene.filterFields.acquisitionDate}, ${scene.filterFields.sunAzimuth}, ${scene.filterFields.sunElevation},
        ${scene.statusFields.thumbnailStatus}, ${scene.statusFields.boundaryStatus},
        ${scene.statusFields.ingestStatus}, ${scene.sceneType.getOrElse(SceneType.Avro)}
      )
    """).update.run


    val thumbnailInsert = ThumbnailDao.insertMany(thumbnails)
    val imageInsert = ImageDao.insertManyImages(images.map(_._1))
    val bandInsert = BandDao.createMany(bands.flatten)
    val sceneWithRelatedquery = SceneWithRelatedDao.getScene(scene.id, user)

    for {
      _ <- sceneInsert
      _ <- thumbnailInsert
      _ <- imageInsert
      _ <- bandInsert
      sceneWithRelated <- sceneWithRelatedquery
    } yield sceneWithRelated
  }


  def update(scene: Scene, id: UUID, user: User): ConnectionIO[(Int, KickoffIngest)] = {
    val idFilter = fr"id = ${id}".some
    val now = new Date()

    val lastModifiedIO: ConnectionIO[Timestamp] =
      (fr"select modified_at from scenes" ++ Fragments.whereAndOpt(ownerEditFilter(user), idFilter))
        .query[Timestamp]
        .unique
    val updateIO: ConnectionIO[Int] = (sql"""
    UPDATE scenes
    SET
      modified_at = ${now},
      modified_by = ${user.id},
      visibility = ${scene.visibility},
      tags = ${scene.tags},
      datasource = ${scene.datasource},
      scene_metadata = ${scene.sceneMetadata},
      name = ${scene.name},
      data_footprint = ${scene.dataFootprint},
      tile_footprint = ${scene.tileFootprint},
      ingest_location = ${scene.ingestLocation},
      cloud_cover = ${scene.filterFields.cloudCover},
      acquisition_date = ${scene.filterFields.acquisitionDate},
      sun_azimuth = ${scene.filterFields.sunAzimuth},
      sun_elevation = ${scene.filterFields.sunElevation},
      thumbnail_status = ${scene.statusFields.thumbnailStatus},
      boundary_status = ${scene.statusFields.boundaryStatus},
      ingest_status = ${scene.statusFields.ingestStatus}
    """ ++ Fragments.whereAndOpt(ownerEditFilter(user), idFilter))
      .update
      .run

    lastModifiedIO flatMap {
      case ts: Timestamp =>
        updateIO map {
          case n =>
            scene.statusFields.ingestStatus match {
              case IngestStatus.ToBeIngested =>
                (n, true)
              case IngestStatus.Ingesting =>
                (n, ts.getTime < now.getTime - (24 hours).toMillis)
              case _ =>
                (n, false)
            }
        }
    }
  }
}
