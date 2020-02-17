package com.rasterfoundry.database

import com.rasterfoundry.common._
import com.rasterfoundry.common.color._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.Cache
import com.rasterfoundry.datamodel.{Scene, User, _}

import cats.effect.{IO, LiftIO}
import cats.implicits._
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.ObjectMetadata
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._
import geotrellis.vector.{Geometry, Polygon, Projected}
import io.circe.syntax._
import scalacache.CatsEffect.modes._
import scalacache._

import scala.concurrent.duration._

import java.net.{URI, URLDecoder}
import java.sql.Timestamp
import java.util.{Date, UUID}

@SuppressWarnings(Array("EmptyCaseClass"))
final case class SceneDao()

object SceneDao
    extends Dao[Scene]
    with LazyLogging
    with ObjectPermissions[Scene]
    with AWSBatch {

  import Cache.GeotiffInfoCache._
  import Cache.SceneCache._

  type KickoffIngest = Boolean

  val tableName = "scenes"

  val selectF: Fragment = sql"""
    SELECT
      id, created_at, created_by, modified_at, owner,
      visibility, tags,
      datasource, scene_metadata, name, tile_footprint,
      data_footprint, metadata_files, ingest_location, cloud_cover,
      acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
      boundary_status, ingest_status, scene_type, data_path, crs,
      band_count, cell_type, grid_extent, resolutions, no_data_value
    FROM
  """ ++ tableF

  def deleteCache(id: UUID): ConnectionIO[Unit] = {
    for {
      _ <- remove(Scene.cacheKey(id))(sceneCache, async[ConnectionIO]).attempt
    } yield ()
  }

  def getSceneById(id: UUID): ConnectionIO[Option[Scene]] = {
    Cache.getOptionCache(Scene.cacheKey(id), Some(30 minutes)) {
      query.filter(id).selectOption
    }
  }

  def getSceneById(sceneId: UUID, footprint: Option[Projected[Polygon]])(
      implicit Filter: Filterable[Any, Projected[Geometry]]
  ): ConnectionIO[Option[Scene]] =
    (selectF ++ Fragments.whereAndOpt(
      (Some(fr"id = ${sceneId}") +: (footprint map {
        Filter.toFilters(_)
      } getOrElse { List.empty })): _*
    )).query[Scene].option

  def unsafeGetSceneById(id: UUID): ConnectionIO[Scene] = {
    cachingF(Scene.cacheKey(id))(Some(30 minutes)) {
      query.filter(id).select
    }
  }

  @SuppressWarnings(Array("CollectionIndexOnNonIndexedSeq"))
  def insert(
      sceneCreate: Scene.Create,
      user: User
  ): ConnectionIO[Scene.WithRelated] = {
    val scene = sceneCreate.toScene(user)
    val thumbnails = sceneCreate.thumbnails.map(_.toThumbnail)
    val images = (sceneCreate.images map { im: Image.Banded =>
      im.toImage(user)
    }).zipWithIndex
    val bands = images map {
      case (im: Image, ind: Int) =>
        sceneCreate.images(ind).bands map { bd =>
          bd.toBand(im.id)
        }
    }

    val sceneInsertId = (fr"INSERT INTO" ++ tableF ++ fr"""(
         id, created_at, created_by, modified_at, owner,
         visibility, tags,
         datasource, scene_metadata, name, tile_footprint,
         data_footprint, metadata_files, ingest_location, cloud_cover,
         acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
         boundary_status, ingest_status, scene_type, data_path, crs, band_count,
         cell_type, grid_extent, resolutions, no_data_value
      )""" ++ fr"""VALUES (
        ${scene.id}, ${scene.createdAt}, ${scene.createdBy}, ${scene.modifiedAt}, ${scene.owner},
        ${scene.visibility}, ${scene.tags}, ${scene.datasource}, ${scene.sceneMetadata}, ${scene.name},
        ST_MakeValid(${scene.tileFootprint}), ST_MakeValid(${scene.dataFootprint}), ${scene.metadataFiles},
        ${scene.ingestLocation}, ${scene.filterFields.cloudCover},
        ${scene.filterFields.acquisitionDate}, ${scene.filterFields.sunAzimuth}, ${scene.filterFields.sunElevation},
        ${scene.statusFields.thumbnailStatus}, ${scene.statusFields.boundaryStatus},
        ${scene.statusFields.ingestStatus}, ${scene.sceneType.getOrElse(
      SceneType.Avro)},
        ${scene.metadataFields.dataPath}, ${scene.metadataFields.crs}, ${scene.metadataFields.bandCount},
        ${scene.metadataFields.cellType}, ${scene.metadataFields.gridExtent}, ${scene.metadataFields.resolutions},
        ${scene.metadataFields.noDataValue}
      )
    """).update.withUniqueGeneratedKeys[UUID]("id")

    val thumbnailInsert = ThumbnailDao.insertMany(thumbnails)
    val imageInsert = ImageDao.insertManyImages(images.map(_._1))
    val bandInsert = BandDao.createMany(bands.flatten)

    for {
      sceneId <- sceneInsertId
      _ <- thumbnailInsert
      _ <- imageInsert
      _ <- bandInsert
      // It's fine to do this unsafely, since we know we the prior insert succeeded
      sceneWithRelated <- SceneWithRelatedDao.unsafeGetScene(sceneId)
      kickoffIngest = sceneWithRelated.statusFields.ingestStatus == IngestStatus.Queued
      copied = if (kickoffIngest) {
        sceneWithRelated.copy(
          statusFields = sceneWithRelated.statusFields
            .copy(ingestStatus = IngestStatus.ToBeIngested)
        )
      } else {
        sceneWithRelated
      }
      _ <- if (kickoffIngest) {
        logger.trace(
          s"Kicking off ingest for newly created scene: ${sceneWithRelated.id} with ingest status ${sceneWithRelated.statusFields.ingestStatus}"
        )
        kickoffSceneIngest(sceneWithRelated.id).pure[ConnectionIO] <*
          SceneDao.update(copied.toScene, sceneWithRelated.id, user)
      } else {
        ().pure[ConnectionIO]
      }
    } yield copied
  }

  def getSceneGeoTiffInfo(
      sceneId: UUID): ConnectionIO[Option[BacksplashGeoTiffInfo]] = {
    Cache.getOptionCache(s"SceneInfo:$sceneId", Some(30 minutes)) {
      sql"SELECT backsplash_geotiff_info FROM scenes WHERE id = $sceneId"
        .query[Option[BacksplashGeoTiffInfo]]
        .unique
    }
  }

  def updateSceneGeoTiffInfo(bsi: BacksplashGeoTiffInfo,
                             id: UUID): ConnectionIO[Int] = {
    fr"""UPDATE scenes SET backsplash_geotiff_info = ${bsi} WHERE id = ${id}""".update.run
  }

  @SuppressWarnings(Array("CollectionIndexOnNonIndexedSeq"))
  def insertMaybe(
      sceneCreate: Scene.Create,
      user: User
  ): ConnectionIO[Option[Scene.WithRelated]] = {
    val scene = sceneCreate.toScene(user)
    val thumbnails = sceneCreate.thumbnails.map(_.toThumbnail)
    val images = (sceneCreate.images map { im: Image.Banded =>
      im.toImage(user)
    }).zipWithIndex
    val bands = images map {
      case (im: Image, ind: Int) =>
        sceneCreate.images(ind).bands map { bd =>
          bd.toBand(im.id)
        }
    }

    val sceneInsert = (Fragment.const(s"""
      INSERT INTO ${tableName} (
         id, created_at, created_by, modified_at, owner,
         visibility, tags,
         datasource, scene_metadata, name, tile_footprint,
         data_footprint, metadata_files, ingest_location, cloud_cover,
         acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
         boundary_status, ingest_status, scene_type, data_path, crs, band_count,
         cell_type, grid_extent, resolutions, no_data_value
      )""") ++ fr"""VALUES (
        ${scene.id}, ${scene.createdAt}, ${scene.createdBy}, ${scene.modifiedAt}, ${scene.owner},
        ${scene.visibility}, ${scene.tags},
         ${scene.datasource}, ${scene.sceneMetadata}, ${scene.name}, ${scene.tileFootprint},
        ${scene.dataFootprint}, ${scene.metadataFiles}, ${scene.ingestLocation}, ${scene.filterFields.cloudCover},
        ${scene.filterFields.acquisitionDate}, ${scene.filterFields.sunAzimuth}, ${scene.filterFields.sunElevation},
        ${scene.statusFields.thumbnailStatus}, ${scene.statusFields.boundaryStatus},
        ${scene.statusFields.ingestStatus}, ${scene.sceneType.getOrElse(
      SceneType.Avro)},
        ${scene.metadataFields.dataPath}, ${scene.metadataFields.crs}, ${scene.metadataFields.bandCount},
        ${scene.metadataFields.cellType}, ${scene.metadataFields.gridExtent}, ${scene.metadataFields.resolutions}, ${scene.metadataFields.noDataValue}
      )
    """).update.run

    val thumbnailInsert = ThumbnailDao.insertMany(thumbnails)
    val imageInsert = ImageDao.insertManyImages(images.map(_._1))
    val bandInsert = BandDao.createMany(bands.flatten)
    val sceneWithRelatedquery = SceneWithRelatedDao.getScene(scene.id)

    for {
      _ <- sceneInsert
      _ <- thumbnailInsert
      _ <- imageInsert
      _ <- bandInsert
      sceneWithRelated <- sceneWithRelatedquery
    } yield sceneWithRelated
  }

  def update(scene: Scene, id: UUID, user: User): ConnectionIO[Int] = {
    val idFilter = fr"id = ${id}".some
    val now = new Date()

    val lastModifiedAndIngestIO: ConnectionIO[(Timestamp, IngestStatus)] =
      (fr"select modified_at, ingest_status from scenes" ++ Fragments
        .whereAndOpt(idFilter))
        .query[(Timestamp, IngestStatus)]
        .unique
    val updateIO: ConnectionIO[Int] = for {
      result <- (sql"""
    UPDATE scenes
    SET
      modified_at = ${now},
      visibility = ${scene.visibility},
      tags = ${scene.tags},
      datasource = ${scene.datasource},
      scene_metadata = ${scene.sceneMetadata},
      name = ${scene.name},
      data_footprint = ${scene.dataFootprint},
      tile_footprint = ${scene.tileFootprint},
      ingest_location = ${scene.ingestLocation},
      scene_type = ${scene.sceneType},
      cloud_cover = ${scene.filterFields.cloudCover},
      acquisition_date = ${scene.filterFields.acquisitionDate},
      sun_azimuth = ${scene.filterFields.sunAzimuth},
      sun_elevation = ${scene.filterFields.sunElevation},
      thumbnail_status = ${scene.statusFields.thumbnailStatus},
      boundary_status = ${scene.statusFields.boundaryStatus},
      ingest_status = ${scene.statusFields.ingestStatus},
      data_path = ${scene.metadataFields.dataPath},
      crs = ${scene.metadataFields.crs},
      band_count = ${scene.metadataFields.bandCount},
      cell_type = ${scene.metadataFields.cellType},
      grid_extent = ${scene.metadataFields.gridExtent},
      resolutions = ${scene.metadataFields.resolutions},
      no_data_value = ${scene.metadataFields.noDataValue}
        """ ++ Fragments.whereAndOpt(idFilter)).update.run
      _ <- deleteCache(id)
    } yield result

    lastModifiedAndIngestIO flatMap {
      case (ts: Timestamp, prevIngestStatus: IngestStatus) =>
        updateIO flatMap { n =>
          (prevIngestStatus, scene.statusFields.ingestStatus) match {
            // update the scene, kickoff the ingest, return the n
            case (IngestStatus.Queued, IngestStatus.Queued) =>
              n.pure[ConnectionIO]
            case (previous, IngestStatus.Queued) =>
              logger.info(
                s"Kicking off scene ingest for scene ${id} which entered state ${IngestStatus.Queued.toString} from ${previous.toString}"
              )
              n.pure[ConnectionIO] <*
                kickoffSceneIngest(id).pure[ConnectionIO] <*
                update(
                  scene.copy(
                    statusFields = scene.statusFields
                      .copy(ingestStatus = IngestStatus.ToBeIngested)
                  ),
                  id,
                  user
                )
            case (IngestStatus.Ingesting, IngestStatus.Ingesting) =>
              if (ts.getTime < now.getTime - (24 hours).toMillis) {
                logger.info(
                  s"Kicking off scene ingest for scene ${id} which has been ingesting for too long"
                )
                n.pure[ConnectionIO] <*
                  kickoffSceneIngest(id).pure[ConnectionIO] <*
                  update(
                    scene.copy(
                      statusFields = scene.statusFields
                        .copy(ingestStatus = IngestStatus.ToBeIngested)
                    ),
                    id,
                    user
                  )
              } else {
                n.pure[ConnectionIO]
              }
            case (previous, IngestStatus.Ingested)
                if previous != IngestStatus.Ingested =>
              SceneToLayerDao
                .getProjectsAndLayersBySceneId(scene.id)
                .flatMap(spls => {
                  for {
                    _ <- spls.traverse(spl =>
                      SceneToLayerDao.deleteMosaicDefCache(spl.projectLayerId))
                  } yield ()
                })
                .map(_ => n)
            case _ =>
              n.pure[ConnectionIO]
          }
        }
    }
  }

  def getMosaicDefinition(
      sceneId: UUID,
      polygonO: Option[Projected[Polygon]],
      redBand: Int,
      greenBand: Int,
      blueBand: Int
  ): ConnectionIO[Seq[MosaicDefinition]] = {
    val polygonF: Fragment = polygonO match {
      case Some(polygon) => fr"ST_Intersects(tile_footprint, ${polygon})"
      case _             => fr""
    }
    for {
      sceneO <- SceneDao.query.filter(sceneId).filter(polygonF).selectOption
    } yield {
      sceneO map { (scene: Scene) =>
        Seq(
          MosaicDefinition(
            scene.id,
            UUID.randomUUID, // we don't have a project id here, so fake it
            scene.datasource,
            scene.name,
            ColorCorrect.Params(
              redBand,
              greenBand,
              blueBand
            ),
            scene.sceneType,
            scene.ingestLocation,
            scene.dataFootprint map { _.geom },
            false,
            Some(().asJson),
            None,
            scene.metadataFields,
            scene.metadataFiles
          )
        )
      } getOrElse { Seq.empty }
    }
  }

  def getSentinelMetadata(metadataUrl: String)(
      implicit L: LiftIO[ConnectionIO]
  ): ConnectionIO[(Array[Byte], ObjectMetadata)] = {
    val s3Client = S3(region = Some(S3RegionEnum(Regions.EU_CENTRAL_1)))
    val bucketAndPrefix =
      s3Client.bucketAndPrefixFromURI(
        new URI(URLDecoder.decode(metadataUrl, "UTF-8"))
      )
    val s3Object =
      s3Client.getObject(bucketAndPrefix._1, bucketAndPrefix._2, true)
    val metaData = S3.getObjectMetadata(s3Object)
    L.liftIO(IO {
      (S3.getObjectBytes(s3Object), metaData)
    })
  }

  def authQuery(
      user: User,
      objectType: ObjectType,
      ownershipTypeO: Option[String] = None,
      groupTypeO: Option[GroupType] = None,
      groupIdO: Option[UUID] = None
  ): Dao.QueryBuilder[Scene] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Scene](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Scene](
          selectF,
          tableF,
          List(
            queryObjectsF(
              user,
              objectType,
              ActionType.View,
              ownershipTypeO,
              groupTypeO,
              groupIdO
            )
          )
        )
    }

  def authorized(
      user: User,
      objectType: ObjectType,
      objectId: UUID,
      actionType: ActionType
  ): ConnectionIO[AuthResult[Scene]] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .selectOption
      .map(AuthResult.fromOption _)
}
