package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.filter.Filterables._
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

import com.lonelyplanet.akka.http.extensions.PageRequest
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon
import io.circe.{Decoder, Encoder, Json}

import scala.reflect.runtime.universe.TypeTag


object SceneDao extends Dao[Scene] {

  val tableName = "scenes"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, ingest_size_bytes, visibility, tags,
      datasource, scene_metadata, name, tile_footprint,
      data_footprint, metadata_files, ingest_location, cloud_cover,
      acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
      boundary_status, ingest_status
    FROM
  """ ++ tableF

  def insert(sceneCreate: Scene.Create, user: User): ConnectionIO[Scene.WithRelated] = {
    val scene = sceneCreate.toScene(user)
    val thumbnails = sceneCreate.thumbnails.map(_.toThumbnail(user.id))
    val images = (sceneCreate.images map { im: Image.Banded => im.toImage(user) }).zipWithIndex
    val bands = images map { case (im: Image, ind: Int) =>
      sceneCreate.images(ind).bands map { bd => bd.toBand(im.id) }
    }

    val sceneInsert = fr"""
      INSERT INTO ${tableName} (
         id, created_at, created_by, modified_at, modified_by, owner,
         organization_id, ingest_size_bytes, visibility, tags,
         datasource, scene_metadata, name, tile_footprint,
         data_footprint, metadata_files, ingest_location, cloud_cover,
         acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
         boundary_status, ingest_status
      ) VALUES (
        ${scene.id}, ${scene.createdAt}, ${scene.createdBy}, ${scene.modifiedAt}, ${scene.modifiedBy}, ${scene.owner},
        ${scene.organizationId}, ${scene.ingestSizeBytes}, ${scene.visibility}, ${scene.tags},
        ${scene.datasource}, ${scene.sceneMetadata}, ${scene.name}, ${scene.tileFootprint},
        ${scene.dataFootprint}, ${scene.metadataFiles}, ${scene.ingestLocation}, ${scene.filterFields.cloudCover},
        ${scene.filterFields.acquisitionDate}, ${scene.filterFields.sunAzimuth}, ${scene.filterFields.sunElevation},
        ${scene.statusFields.thumbnailStatus}, ${scene.statusFields.boundaryStatus}, ${scene.statusFields.ingestStatus}
      )
    """.update.run


    val thumbnailInsert = ThumbnailDao.insertMany(thumbnails)
    val imageInsert = ImageDao.insertManyImages(images.map(_._1))
    val bandInsert = BandDao.createMany(bands.flatten)
    val sceneWithRelatedquery = SceneWithRelatedDao.query.filter(scene.id).select

    for {
      _ <- sceneInsert
      _ <- thumbnailInsert
      _ <- imageInsert
      _ <- bandInsert
      sceneWithRelated <- sceneWithRelatedquery
    } yield sceneWithRelated
  }

  def update(scene: Scene, id: UUID, user: User): ConnectionIO[(Int, Boolean)] = {

    ???
  }
}


object SceneWithRelatedDao extends Dao[Scene.WithRelated] {
  val tableName = "scenes"

  def projectFilterFragment(projectId: UUID): Fragment = fr"scenes.id IN (SELECT scene_id FROM scenes_to_projects WHERE project_id = ${projectId})"

  def listProjectScenes(projectId: UUID, page: PageRequest, sceneParams: CombinedSceneQueryParams, user: User): ConnectionIO[PaginatedResponse[Scene.WithRelated]] = {
    query.filter(projectFilterFragment(projectId)).filter(sceneParams).ownerFilter(user).page(page)
  }

  val selectF = sql"""
                     SELECT scenes.id,
                            scenes.created_at,
                            scenes.created_by,
                            scenes.modified_at,
                            scenes.modified_by,
                            scenes.owner,
                            scenes.organization_id,
                            scenes.ingest_size_bytes,
                            scenes.visibility,
                            scenes.tags,
                            scenes.datasource,
                            scenes.scene_metadata,
                            scenes.name,
                            scenes.tile_footprint,
                            scenes.data_footprint,
                            scenes.metadata_files,
                            images_with_bands.j AS images,
                            tnails.thumbnails,
                            scenes.ingest_location,
                            scenes.cloud_cover,
                            scenes.acquisition_date,
                            scenes.sun_azimuth,
                            scenes.sun_elevation,
                            scenes.thumbnail_status,
                            scenes.boundary_status,
                            scenes.ingest_status
                     FROM scenes
                     LEFT JOIN
                       (SELECT scene,
                               jsonb_agg(
                                 jsonb_set(
                                   json_build_object(
                                     'id', i.id,
                                     'createdAt', to_char(i.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                                     'modifiedAt', to_char(i.modified_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                                     'organizationId', i.organization_id,
                                     'createdBy', i.created_by,
                                     'modifiedBy', i.modified_by,
                                     'owner', i.owner,
                                     'rawDataBytes', i.raw_data_bytes,
                                     'visibility', i.visibility,
                                     'filename', i.filename,
                                     'sourceUri', i.sourceuri,
                                     'scene', i.scene,
                                     'imageMetadata', i.image_metadata,
                                     'resolutionMeters', i.resolution_meters,
                                     'metadataFiles', i.metadata_files
                                   )::JSONB, '{bands}', b.bands ::JSONB, TRUE
                                 )
                               ) AS j
                        FROM images AS i
                        LEFT JOIN
                          (-- bands
                      SELECT image_id,
                             jsonb_agg(
                               json_build_object(
                                 'id', b.id,
                                 'image', b.image_id,
                                 'name', b.name,
                                 'number', b.number,
                                 'wavelength', b.wavelength
                               )
                             ) AS bands
                           FROM bands AS b
                           GROUP BY image_id) AS b ON i.id = b.image_id
                        GROUP BY scene) AS images_with_bands ON scenes.id = images_with_bands.scene
                     LEFT JOIN
                       (SELECT scene,
                               jsonb_agg(
                                 json_build_object(
                                   'id', t.id,
                                   'createdAt', to_char(t.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                                   'modifiedAt', to_char(t.modified_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                                   'organizationId', t.organization_id,
                                   'widthPx', t.width_px,
                                   'heightPx', t.height_px,
                                   'sceneId', t.scene,
                                   'url', t.url,
                                   'thumbnailSize', t.thumbnail_size
                                 )
                               ) AS thumbnails
                        FROM thumbnails AS t
                        GROUP BY scene
                       ) AS tnails ON scenes.id = tnails.scene
    """
}