package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util.Page
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

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import java.util.UUID

object SceneWithRelatedDao extends Dao[Scene.WithRelated] {
  val tableName = "scenes"

  val selectF = SceneDao.selectF

  def listProjectScenes(projectId: UUID, pageRequest: PageRequest, sceneParams: CombinedSceneQueryParams, user: User): ConnectionIO[PaginatedResponse[Scene.WithRelated]] = {

    val projectFilterFragment = fr"id IN (SELECT scene_id FROM scenes_to_projects WHERE project_id = ${projectId})"
    val queryFilters = makeFilters(List(sceneParams)).flatten ++ List(Some(projectFilterFragment))
    val paginatedQuery = listQuery(queryFilters, Some(pageRequest)).query[Scene.WithRelated].list
    val countQuery = (fr"SELECT count(*) FROM scenes" ++ Fragments.whereAndOpt(queryFilters: _*)).query[Int].unique

    for {
      page <- paginatedQuery
      count <- query.countIO
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = ((pageRequest.offset + 1) * pageRequest.limit) < count

      PaginatedResponse[Scene.WithRelated](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
    }
  }

  def getScenesImages(sceneIds: List[UUID]): ConnectionIO[List[Image.WithRelated]] =
    sceneIds.toNel match {
      case Some(ids) =>
        ImageDao.query.filter(Fragments.in(fr"scene", ids)).list flatMap {
          ImageWithRelatedDao.imagesToImagesWithRelated
        }
      case _ =>
        List.empty[Image.WithRelated].pure[ConnectionIO]
    }

  def getScenesThumbnails(sceneIds: List[UUID]): ConnectionIO[List[Thumbnail]] =
    sceneIds.toNel match {
      case Some(ids) =>
        ThumbnailDao.query.filter(Fragments.in(fr"scene", ids)).list
      case _ =>
        List.empty[Thumbnail].pure[ConnectionIO]
    }

  def scenesToScenesWithRelated(scenes: List[Scene]): ConnectionIO[List[Scene.WithRelated]] = {
    // "The astute among you will note that we don’t actually need a monad to do this;
    // an applicative functor is all we need here."
    // let's roll, doobie
    val componentsIO: ConnectionIO[(List[Image.WithRelated], List[Thumbnail])] = {
      val thumbnails = getScenesThumbnails(scenes map { _.id  })
      val images = getScenesImages(scenes map { _.id })
      (images, thumbnails).tupled
    }

    componentsIO map {
      case (images, thumbnails) => {
        val groupedThumbs = thumbnails.groupBy(_.sceneId)
        val groupedIms = images.groupBy(_.scene)
        scenes map { scene: Scene =>
          scene.withRelatedFromComponents(
            groupedIms.getOrElse(scene.id, List.empty[Image.WithRelated]),
            groupedThumbs.getOrElse(scene.id, List.empty[Thumbnail])
          )
        }
      }
    }
  }

  def sceneToSceneWithRelated(scene: Scene): ConnectionIO[Scene.WithRelated] = {
    val componentsIO: ConnectionIO[(List[Image.WithRelated], List[Thumbnail])] = {
      val thumbnails = getScenesThumbnails(List(scene.id))
      val images = getScenesImages(List(scene.id))
      (images, thumbnails).tupled
    }

    componentsIO map {
      case (images, thumbnails) => {
        scene.withRelatedFromComponents(images, thumbnails)
      }
    }
  }

  def sceneOToSceneWithRelatedO(sceneO: Option[Scene]): ConnectionIO[Option[Scene.WithRelated]] = {
    sceneO match {
      case Some(scene) => sceneToSceneWithRelated(scene) map { _.some }
      case None => Option.empty[Scene.WithRelated].pure[ConnectionIO]
    }
  }

  def listScenes(pageRequest: PageRequest, sceneParams: CombinedSceneQueryParams, user: User): ConnectionIO[PaginatedResponse[Scene.WithRelated]] = {

    val pageFragment: Fragment = Page(pageRequest)
    val queryFilters: List[Option[Fragment]] = makeFilters(List(sceneParams)).flatten
    val scenesIO: ConnectionIO[List[Scene]] =
      (selectF ++ Fragments.whereAndOpt(queryFilters: _*) ++ pageFragment)
        .query[Scene]
        .stream
        .compile
        .toList
    val withRelatedsIO: ConnectionIO[List[Scene.WithRelated]] = scenesIO flatMap { scenesToScenesWithRelated }

    for {
      page <- withRelatedsIO
      count <- query.countIO
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = ((pageRequest.offset + 1) * pageRequest.limit) < count
      PaginatedResponse[Scene.WithRelated](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
    }
  }

  def getSceneQ(sceneId: UUID, user: User) = {
    (selectF ++ Fragments.whereAndOpt(fr"id = ${sceneId}".some, ownerEditFilter(user)))
      .query[Scene]
  }

  def getScene(sceneId: UUID, user: User): ConnectionIO[Option[Scene.WithRelated]] = {
    val scenesO: ConnectionIO[Option[Scene]] = getSceneQ(sceneId, user).option
    scenesO map { _.toList } flatMap { scenesToScenesWithRelated(_) } map {
      // guaranteed to be either 0 or 1 in the list based on .option above, so no need to worry
      // about losing information from lists with length > 1
      _.headOption
    }
  }

  @SuppressWarnings(Array("TraversableHead"))
  def unsafeGetScene(sceneId: UUID, user: User): ConnectionIO[Scene.WithRelated] = {
    val sceneIO: ConnectionIO[Scene] = getSceneQ(sceneId, user).unique
    // head is guaranteed to to succeed if the id is present, which is appropriate for a method marked
    // unsafe
    sceneIO flatMap { (scene: Scene) => scenesToScenesWithRelated(List(scene)) } map { _.head }
  }

  def getScenesToIngest(projectId: UUID): ConnectionIO[List[Scene.WithRelated]] = {
    val fragments = List(
      Some(fr"""(ingest_status = ${IngestStatus.ToBeIngested.toString} :: ingest_status
           OR (ingest_status = ${IngestStatus.Ingesting.toString} :: ingest_status AND (now() - modified_at) > '1 day'::interval))
        """),
      Some(fr"scenes.id IN (SELECT scene_id FROM scenes_to_projects WHERE project_id = ${projectId})")
    )
    listQuery(fragments, None).query[Scene.WithRelated].list
  }

  def listQuery(fragments: List[Option[Fragment]], page: Option[PageRequest]): Fragment = {
      fr"""WITH
        scenes_q AS (
          SELECT *, coalesce(acquisition_date, created_at) as acquisition_date_sort
          FROM scenes""" ++ Fragments.whereAndOpt(fragments: _*) ++
        fr"""
        ), images_q AS (
          SELECT * FROM images WHERE images.scene IN (SELECT id FROM scenes_q)
        ), thumbnails_q AS (
          SELECT * FROM thumbnails WHERE thumbnails.scene IN (SELECT id FROM scenes_q)
        ), bands_q AS (
          SELECT * FROM bands WHERE image_id IN (SELECT id FROM images_q)
        )
      SELECT scenes_q.id,
             scenes_q.created_at,
             scenes_q.created_by,
             scenes_q.modified_at,
             scenes_q.modified_by,
             scenes_q.owner,
             scenes_q.organization_id,
             scenes_q.ingest_size_bytes,
             scenes_q.visibility,
             scenes_q.tags,
             scenes_q.datasource,
             scenes_q.scene_metadata,
             scenes_q.name,
             scenes_q.tile_footprint,
             scenes_q.data_footprint,
             scenes_q.metadata_files,
             images_with_bands.j :: jsonb AS images,
             tnails.thumbnails :: jsonb,
             scenes_q.ingest_location,
             scenes_q.cloud_cover,
             scenes_q.acquisition_date,
             scenes_q.sun_azimuth,
             scenes_q.sun_elevation,
             scenes_q.thumbnail_status,
             scenes_q.boundary_status,
             scenes_q.ingest_status
      FROM scenes_q
      LEFT JOIN (
        SELECT
          scene,
          json_agg (
            json_build_object(
              'id', i.id,
              'createdAt', to_char(i.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              'modifiedAt', to_char(i.modified_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
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
              'metadataFiles', i.metadata_files,
              'bands', b.bands
            )
          ) AS j
        FROM images_q AS i
        LEFT JOIN (
          SELECT
            image_id,
            json_agg(
              json_build_object(
                'id', b.id,
                'image', b.image_id,
                'name', b.name,
                'number', b.number,
                'wavelength', b.wavelength
              )
            ) AS bands
            FROM bands_q AS b
            GROUP BY image_id
          ) AS b ON i.id = b.image_id
        GROUP BY scene
      ) AS images_with_bands ON scenes_q.id = images_with_bands.scene
      LEFT JOIN (
        SELECT
          scene,
          json_agg(
            json_build_object(
              'id', t.id,
              'createdAt', to_char(t.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              'modifiedAt', to_char(t.modified_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
              'organizationId', t.organization_id,
              'widthPx', t.width_px,
              'heightPx', t.height_px,
              'sceneId', t.scene,
              'url', t.url,
              'thumbnailSize', t.thumbnail_size
            )
          ) AS thumbnails
        FROM thumbnails_q AS t
        GROUP BY scene
      ) AS tnails ON scenes_q.id = tnails.scene""" ++ Page(page)
  }

  def makeFilters[T](myList: List[T])(implicit filterable: Filterable[Scene.WithRelated, T]) = {
    myList.map(filterable.toFilters(_))
  }
}
