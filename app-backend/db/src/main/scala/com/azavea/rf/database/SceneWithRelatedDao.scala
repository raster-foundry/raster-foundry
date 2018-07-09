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
    val andPendingF: Fragment = sceneParams.sceneParams.pending match {
      case Some(true) => fr"AND accepted = false"
      case _ => fr"AND accepted = true"
    }
    val projectFilterFragment: Fragment = fr"""
      id IN (
        SELECT scene_id
        FROM scenes_to_projects
        WHERE
          project_id = ${projectId}""" ++ andPendingF ++ fr")"
    val queryFilters = makeFilters(List(sceneParams)).flatten ++ List(Some(projectFilterFragment))
    val paginatedQuery = SceneDao.query.filter(queryFilters).list(pageRequest.offset, pageRequest.limit) flatMap {
      (scenes: List[Scene]) => scenesToScenesWithRelated(scenes)
    }

    for {
      page <- paginatedQuery
      count <- query.filter(projectFilterFragment).countIO
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = ((pageRequest.offset + 1) * pageRequest.limit) < count
      PaginatedResponse[Scene.WithRelated](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
    }
  }

  def listAuthorizedScenes(pageRequest: PageRequest, sceneParams: CombinedSceneQueryParams, user: User, ownershipTypeO: Option[String] = None,
    groupTypeO: Option[GroupType] = None, groupIdO: Option[UUID] = None): ConnectionIO[PaginatedResponse[Scene.WithRelated]] = for {
    shapeO <- sceneParams.sceneParams.shape match {
      case Some(shpId) => ShapeDao.getShapeById(shpId)
      case _ => None.pure[ConnectionIO]
    }
    sceneSearchBuilder = {
      SceneDao
        .authViewQuery(
          user,
          ObjectType.Scene,
          sceneParams.ownershipTypeParams.ownershipType,
          sceneParams.groupQueryParameters.groupType,
          sceneParams.groupQueryParameters.groupId
        )
        .filter(shapeO map { _.geometry })
        .filter(sceneParams)
    }
    scenes <- sceneSearchBuilder.list(
      (pageRequest.offset * pageRequest.limit),
      pageRequest.limit,
      fr"ORDER BY coalesce (acquisition_date, created_at) DESC, id DESC"
    )
    withRelateds <- scenesToScenesWithRelated(scenes)
    count <- sceneSearchBuilder.countIO
  } yield {
    val hasPrevious = pageRequest.offset > 0
    val hasNext = ((pageRequest.offset + 1) * pageRequest.limit) < count
    PaginatedResponse[Scene.WithRelated](
      count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, withRelateds
    )
  }

  def getScenesDatasources(datasourceIds: List[UUID]): ConnectionIO[List[Datasource]] =
    datasourceIds.toNel match {
      case Some(ids) => {
        DatasourceDao.query.filter(Fragments.in(fr"id", ids)).list
      }
      case _ => List.empty[Datasource].pure[ConnectionIO]
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

  // We know the datasources list head exists because of the foreign key relationship
  @SuppressWarnings(Array("TraversableHead"))
  def scenesToScenesWithRelated(scenes: List[Scene]): ConnectionIO[List[Scene.WithRelated]] = {
    // "The astute among you will note that we don’t actually need a monad to do this;
    // an applicative functor is all we need here."
    // let's roll, doobie
    val componentsIO: ConnectionIO[(List[Image.WithRelated], List[Thumbnail], List[Datasource])] = {
      val thumbnails = getScenesThumbnails(scenes map { _.id  })
      val images = getScenesImages(scenes map { _.id })
      val datasources = getScenesDatasources(scenes map { _.datasource })
      (images, thumbnails, datasources).tupled
    }

    componentsIO map {
      case (images, thumbnails, datasources) => {
        val groupedThumbs = thumbnails.groupBy(_.sceneId)
        val groupedIms = images.groupBy(_.scene)
        scenes map { scene: Scene =>
          scene.withRelatedFromComponents(
            groupedIms.getOrElse(scene.id, List.empty[Image.WithRelated]),
            groupedThumbs.getOrElse(scene.id, List.empty[Thumbnail]),
            datasources.filter(_.id == scene.datasource).head
          )
        }
      }
    }
  }

  def sceneToSceneWithRelated(scene: Scene): ConnectionIO[Scene.WithRelated] = {
    val componentsIO: ConnectionIO[(List[Image.WithRelated], List[Thumbnail], Datasource)] = {
      val thumbnails = getScenesThumbnails(List(scene.id))
      val images = getScenesImages(List(scene.id))
      val datasource = DatasourceDao.unsafeGetDatasourceById(scene.datasource)
      (images, thumbnails, datasource).tupled
    }

    componentsIO map {
      case (images, thumbnails, datasource) => {
        scene.withRelatedFromComponents(images, thumbnails, datasource)
      }
    }
  }

  def sceneOToSceneWithRelatedO(sceneO: Option[Scene]): ConnectionIO[Option[Scene.WithRelated]] = {
    sceneO match {
      case Some(scene) => sceneToSceneWithRelated(scene) map { _.some }
      case None => Option.empty[Scene.WithRelated].pure[ConnectionIO]
    }
  }

  def getSceneQ(sceneId: UUID, user: User) = {
    (selectF ++ Fragments.whereAnd(fr"id = ${sceneId}"))
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
    SceneDao.query.filter(fragments).list flatMap {
      (scenes: List[Scene]) => scenesToScenesWithRelated(scenes)
    }
  }

  def makeFilters[T](myList: List[T])(implicit filterable: Filterable[Scene.WithRelated, T]) = {
    myList.map(filterable.toFilters(_))
  }
}
