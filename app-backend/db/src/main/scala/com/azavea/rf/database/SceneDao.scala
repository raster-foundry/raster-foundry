package com.azavea.rf.database

import java.sql.Timestamp
import java.util.{Date, UUID}

import cats.implicits._
import com.azavea.rf.common.AWSBatch
import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.color._
import com.azavea.rf.datamodel.{Scene, User, _}
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import geotrellis.vector.{Polygon, Projected}
import io.circe.optics.JsonPath._

import scala.concurrent.duration._

object SceneDao
    extends Dao[Scene]
    with LazyLogging
    with ObjectPermissions[Scene]
    with AWSBatch {

  type KickoffIngest = Boolean

  val tableName = "scenes"

  val selectF: Fragment = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      visibility, tags,
      datasource, scene_metadata, name, tile_footprint,
      data_footprint, metadata_files, ingest_location, cloud_cover,
      acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
      boundary_status, ingest_status, scene_type
    FROM
  """ ++ tableF

  def getSceneById(id: UUID): ConnectionIO[Option[Scene]] =
    query.filter(id).selectOption

  def unsafeGetSceneById(id: UUID): ConnectionIO[Scene] =
    query.filter(id).select

  def getSceneDatasource(sceneId: UUID): ConnectionIO[Datasource] =
    unsafeGetSceneById(sceneId) flatMap { scene: Scene =>
      DatasourceDao.unsafeGetDatasourceById(scene.datasource)
    }

  @SuppressWarnings(Array("CollectionIndexOnNonIndexedSeq"))
  def insert(sceneCreate: Scene.Create,
             user: User): ConnectionIO[Scene.WithRelated] = {
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
         id, created_at, created_by, modified_at, modified_by, owner,
         visibility, tags,
         datasource, scene_metadata, name, tile_footprint,
         data_footprint, metadata_files, ingest_location, cloud_cover,
         acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
         boundary_status, ingest_status, scene_type
      )""" ++ fr"""VALUES (
        ${scene.id}, ${scene.createdAt}, ${scene.createdBy}, ${scene.modifiedAt}, ${scene.modifiedBy}, ${scene.owner},
        ${scene.visibility}, ${scene.tags}, ${scene.datasource}, ${scene.sceneMetadata}, ${scene.name},
        ST_MakeValid(${scene.tileFootprint}), ST_MakeValid(${scene.dataFootprint}), ${scene.metadataFiles},
        ${scene.ingestLocation}, ${scene.filterFields.cloudCover},
        ${scene.filterFields.acquisitionDate}, ${scene.filterFields.sunAzimuth}, ${scene.filterFields.sunElevation},
        ${scene.statusFields.thumbnailStatus}, ${scene.statusFields.boundaryStatus},
        ${scene.statusFields.ingestStatus}, ${scene.sceneType.getOrElse(
      SceneType.Avro)}
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
          statusFields = sceneWithRelated.statusFields.copy(
            ingestStatus = IngestStatus.ToBeIngested))
      } else {
        sceneWithRelated
      }
      _ <- if (kickoffIngest) {
        logger.info(
          s"Kicking off ingest for newly created scene: ${sceneWithRelated.id} with ingest status ${sceneWithRelated.statusFields.ingestStatus}"
        )
        kickoffSceneIngest(sceneWithRelated.id).pure[ConnectionIO] <*
          SceneDao.update(copied.toScene, sceneWithRelated.id, user)
      } else {
        ().pure[ConnectionIO]
      }
    } yield copied
  }

  @SuppressWarnings(Array("CollectionIndexOnNonIndexedSeq"))
  def insertMaybe(sceneCreate: Scene.Create,
                  user: User): ConnectionIO[Option[Scene.WithRelated]] = {
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
         id, created_at, created_by, modified_at, modified_by, owner,
         visibility, tags,
         datasource, scene_metadata, name, tile_footprint,
         data_footprint, metadata_files, ingest_location, cloud_cover,
         acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
         boundary_status, ingest_status, scene_type
      )""") ++ fr"""VALUES (
        ${scene.id}, ${scene.createdAt}, ${scene.createdBy}, ${scene.modifiedAt}, ${scene.modifiedBy}, ${scene.owner},
        ${scene.visibility}, ${scene.tags},
         ${scene.datasource}, ${scene.sceneMetadata}, ${scene.name}, ${scene.tileFootprint},
        ${scene.dataFootprint}, ${scene.metadataFiles}, ${scene.ingestLocation}, ${scene.filterFields.cloudCover},
        ${scene.filterFields.acquisitionDate}, ${scene.filterFields.sunAzimuth}, ${scene.filterFields.sunElevation},
        ${scene.statusFields.thumbnailStatus}, ${scene.statusFields.boundaryStatus},
        ${scene.statusFields.ingestStatus}, ${scene.sceneType.getOrElse(
      SceneType.Avro)}
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
      scene_type = ${scene.sceneType},
      cloud_cover = ${scene.filterFields.cloudCover},
      acquisition_date = ${scene.filterFields.acquisitionDate},
      sun_azimuth = ${scene.filterFields.sunAzimuth},
      sun_elevation = ${scene.filterFields.sunElevation},
      thumbnail_status = ${scene.statusFields.thumbnailStatus},
      boundary_status = ${scene.statusFields.boundaryStatus},
      ingest_status = ${scene.statusFields.ingestStatus}
    """ ++ Fragments.whereAndOpt(idFilter)).update.run

    lastModifiedAndIngestIO flatMap {
      case (ts: Timestamp, prevIngestStatus: IngestStatus) =>
        updateIO flatMap { n =>
          (prevIngestStatus, scene.statusFields.ingestStatus) match {
            // update the scene, kickoff the ingest, return the n
            case (IngestStatus.Queued, IngestStatus.Queued) =>
              n.pure[ConnectionIO]
            case (previous, IngestStatus.Queued) =>
              logger.info(
                s"Kicking off scene ingest for scene ${id} which entered state ${IngestStatus.Queued.toString} from ${previous.toString}")
              n.pure[ConnectionIO] <*
                kickoffSceneIngest(id).pure[ConnectionIO] <*
                update(scene.copy(
                         statusFields = scene.statusFields.copy(
                           ingestStatus = IngestStatus.ToBeIngested)),
                       id,
                       user)
            case (IngestStatus.Ingesting, IngestStatus.Ingesting) =>
              if (ts.getTime < now.getTime - (24 hours).toMillis) {
                logger.info(
                  s"Kicking off scene ingest for scene ${id} which has been ingesting for too long")
                n.pure[ConnectionIO] <*
                  kickoffSceneIngest(id).pure[ConnectionIO] <*
                  update(scene.copy(
                           statusFields = scene.statusFields.copy(
                             ingestStatus = IngestStatus.ToBeIngested)),
                         id,
                         user)
              } else {
                n.pure[ConnectionIO]
              }
            case _ =>
              n.pure[ConnectionIO]
          }
        }
    }
  }

  def getMosaicDefinition(sceneId: UUID, polygonO: Option[Projected[Polygon]])
    : ConnectionIO[Seq[MosaicDefinition]] = {
    val polygonF: Fragment = polygonO match {
      case Some(polygon) => fr"ST_Intersects(tile_footprint, ${polygon})"
      case _             => fr""
    }
    for {
      sceneO <- SceneDao.query.filter(sceneId).filter(polygonF).selectOption
      datasourceO <- sceneO match {
        case Some(s: Scene) =>
          DatasourceDao.query.filter(s.datasource).selectOption
        case _ => None.pure[ConnectionIO]
      }
    } yield {
      (sceneO, datasourceO) match {
        case (Some(scene: Scene), Some(datasource: Datasource)) =>
          val composites = datasource.composites
          val redBandPath = root.natural.selectDynamic("value").redBand.int
          val greenBandPath = root.natural.selectDynamic("value").greenBand.int
          val blueBandPath = root.natural.selectDynamic("value").blueBand.int

          Seq(
            MosaicDefinition(
              scene.id,
              ColorCorrect.Params(
                redBandPath.getOption(composites).getOrElse(0),
                greenBandPath.getOption(composites).getOrElse(1),
                blueBandPath.getOption(composites).getOrElse(2),
                BandGamma(enabled = false, None, None, None),
                PerBandClipping(enabled = false,
                                None,
                                None,
                                None,
                                None,
                                None,
                                None),
                MultiBandClipping(enabled = false, None, None),
                SigmoidalContrast(enabled = false, None, None),
                Saturation(enabled = false, None),
                Equalization(false),
                AutoWhiteBalance(false)
              ),
              scene.sceneType,
              scene.ingestLocation
            ))
        case _ => Seq.empty
      }
    }
  }

  def authQuery(user: User,
                objectType: ObjectType,
                ownershipTypeO: Option[String] = None,
                groupTypeO: Option[GroupType] = None,
                groupIdO: Option[UUID] = None): Dao.QueryBuilder[Scene] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Scene](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Scene](selectF,
                                tableF,
                                List(
                                  queryObjectsF(user,
                                                objectType,
                                                ActionType.View,
                                                ownershipTypeO,
                                                groupTypeO,
                                                groupIdO)))
    }

  def authorized(user: User,
                 objectType: ObjectType,
                 objectId: UUID,
                 actionType: ActionType): ConnectionIO[Boolean] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .exists
}
