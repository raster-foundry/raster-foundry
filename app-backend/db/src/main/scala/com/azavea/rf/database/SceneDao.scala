package com.azavea.rf.database

import java.sql.Timestamp
import java.util.{Date, UUID}

import cats.implicits._
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

object SceneDao extends Dao[Scene] with LazyLogging with ObjectPermissions[Scene] {

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

  // def authViewQuery(user: User,
  //                   ownershipTypeO: Option[String] = None,
  //                   groupTypeO: Option[GroupType] = None,
  //                   groupIdO: Option[UUID] = None): Dao.QueryBuilder[Scene] = {
  //   if (user.isSuperuser) {
  //     Dao.QueryBuilder[Scene](selectF, tableF, List.empty)
  //   } else {
  //     val ownedF: Fragment = fr"owner = ${user.id}"
  //     val visibilityF: Fragment = fr"visibility = 'PUBLIC'"
  //     val sharedF: Fragment = fr"""
  //       SELECT acr.object_id
  //       FROM access_control_rules acr
  //       WHERE acr.object_type = 'SCENE'
  //       AND acr.action_type = 'VIEW'
  //       AND (acr.subject_type = 'ALL' OR (acr.subject_type = 'USER'
  //          AND acr.subject_id = ${user.id}))
  //     """
  //     val inheritedBaseF: Fragment = fr"""
  //       SELECT acr.object_id
  //       FROM access_control_rules acr
  //       JOIN user_group_roles ugr ON acr.subject_type::text = ugr.group_type::text
  //       AND acr.subject_id::text = ugr.group_id::text
  //       WHERE ugr.user_id = ${user.id}
  //         AND acr.object_type = 'SCENE'
  //         AND acr.action_type = 'VIEW'
  //     """
  //     val inheritedF: Fragment = (groupTypeO, groupIdO) match {
  //       case (Some(groupType), Some(groupId)) => inheritedBaseF ++ fr"""
  //        AND ugr.group_type = ${groupType}
  //        AND ugr.group_id = ${groupId}
  //     """
  //       case _                                => inheritedBaseF
  //     }
  //
  //     val authFilter = ownershipTypeO match {
  //       // owned by the requesting user only
  //       case Some(ownershipType) if ownershipType == "owned" =>
  //         fr"(" ++ ownedF ++ fr")"
  //       // shared to the requesting user directly, across platform, or due to group membership
  //       case Some(ownershipType) if ownershipType == "shared" =>
  //         fr"(" ++ visibilityF ++
  //           fr"OR id in(" ++ sharedF ++ fr")" ++
  //           fr"OR id in(" ++ inheritedF ++ fr"))"
  //       // shared to the requesting user due to group membership
  //       case Some(ownershipType) if ownershipType == "inherited" =>
  //         fr"(id in(" ++ inheritedF ++ fr"))"
  //       // the default
  //       case _ =>
  //         fr"(" ++ ownedF ++
  //           fr"OR" ++ visibilityF ++
  //           fr"OR id in(" ++ sharedF ++ fr")" ++
  //           fr"OR id in(" ++ inheritedF ++ fr"))"
  //     }
  //     Dao.QueryBuilder[Scene](selectF, tableF, List(Some(authFilter)))
  //   }
  // }

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
    } yield sceneWithRelated
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

  def update(scene: Scene,
             id: UUID,
             user: User): ConnectionIO[(Int, KickoffIngest)] = {
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
        updateIO map { n =>
          (prevIngestStatus, scene.statusFields.ingestStatus) match {
            case (IngestStatus.ToBeIngested, IngestStatus.ToBeIngested) =>
              (n, true)
            case (IngestStatus.Ingesting, IngestStatus.Ingesting) =>
              (n, ts.getTime < now.getTime - (24 hours).toMillis)
            case _ =>
              (n, false)
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

  def authQuery(
      user: User,
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
