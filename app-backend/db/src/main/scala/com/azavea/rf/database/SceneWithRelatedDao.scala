package com.azavea.rf.database

import java.util.UUID

import cats.data._
import cats.implicits._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.{Scene, User, _}
import com.lonelyplanet.akka.http.extensions.PageRequest
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

object SceneWithRelatedDao
    extends Dao[Scene.WithRelated]
    with ObjectPermissions[Scene.WithRelated] {

  val tableName = "scenes"

  val selectF: doobie.Fragment = SceneDao.selectF

  def listAuthorizedScenes(
      pageRequest: PageRequest,
      sceneParams: CombinedSceneQueryParams,
      user: User): ConnectionIO[PaginatedResponse[Scene.Browse]] =
    for {
      shapeO <- sceneParams.sceneParams.shape match {
        case Some(shpId) => ShapeDao.getShapeById(shpId)
        case _           => None.pure[ConnectionIO]
      }
      sceneSearchBuilder = {
        SceneDao
          .authQuery(
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
        pageRequest.offset * pageRequest.limit,
        pageRequest.limit,
        fr"ORDER BY coalesce (acquisition_date, created_at) DESC, id DESC"
      )
      sceneBrowses <- scenesToSceneBrowse(scenes,
                                          sceneParams.sceneParams.project)
      count <- sceneSearchBuilder.sceneCountIO
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = ((pageRequest.offset + 1) * pageRequest.limit) < count
      PaginatedResponse[Scene.Browse](
        count,
        hasPrevious,
        hasNext,
        pageRequest.offset,
        pageRequest.limit,
        sceneBrowses
      )
    }

  def getScenesDatasources(
      datasourceIds: List[UUID]): ConnectionIO[List[Datasource]] =
    datasourceIds.toNel match {
      case Some(ids) =>
        DatasourceDao.query.filter(Fragments.in(fr"id", ids)).list
      case _ => List.empty[Datasource].pure[ConnectionIO]
    }

  def getScenesInProject(sceneIds: NonEmptyList[UUID], projectId: UUID) =
    (fr"""SELECT id, CASE WHEN sp.project_id IS NULL THEN false ELSE true END as in_project
             FROM scenes LEFT OUTER JOIN scenes_to_projects sp
             ON scenes.id = sp.scene_id""" ++
      Fragments.whereAnd(
        Fragments.in(fr"scenes.id", sceneIds),
        fr"sp.project_id = $projectId")).query[(UUID, Boolean)].to[List]

  def getScenesImages(
      sceneIds: List[UUID]): ConnectionIO[List[Image.WithRelated]] =
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

  def getSceneToProjects(sceneIds: List[UUID],
                         projectId: UUID): ConnectionIO[List[SceneToProject]] =
    sceneIds.toNel match {
      case Some(ids) =>
        SceneToProjectDao.query
          .filter(Fragments.in(fr"scene_id", ids))
          .filter(fr"project_id=$projectId")
          .list
      case _ =>
        List.empty[SceneToProject].pure[ConnectionIO]
    }

  // We know the datasources list head exists because of the foreign key relationship
  @SuppressWarnings(Array("FilterDotHead", "TraversableHead"))
  def scenesToSceneBrowse(
      scenes: List[Scene],
      projectIdO: Option[UUID]): ConnectionIO[List[Scene.Browse]] = {
    // "The astute among you will note that we don’t actually need a monad to do this;
    // an applicative functor is all we need here."
    // let's roll, doobie
    val componentsIO: ConnectionIO[
      (List[Thumbnail], List[Datasource], List[(UUID, Boolean)])] = {
      val thumbnails = getScenesThumbnails(scenes map { _.id })
      val datasources = getScenesDatasources(scenes map { _.datasource })
      val inProjects = (scenes.toNel, projectIdO) match {
        case (Some(s), Some(p)) =>
          getScenesInProject(s map { _.id }, p)
        case _ => List.empty[(UUID, Boolean)].pure[ConnectionIO]
      }
      (thumbnails, datasources, inProjects).tupled
    }

    componentsIO map {
      case (thumbnails, datasources, inProjects) =>
        val groupedThumbs = thumbnails.groupBy(_.sceneId)
        scenes map { scene: Scene =>
          scene.browseFromComponents(
            groupedThumbs.getOrElse(scene.id, List.empty[Thumbnail]),
            datasources.filter(_.id == scene.datasource).head,
            inProjects.filter(_._1 == scene.id).headOption.map(_._2)
          )
        }
    }
  }

  // We know the datasources list head exists because of the foreign key relationship
  @SuppressWarnings(Array("FilterDotHead", "TraversableHead"))
  def scenesToScenesWithRelated(
      scenes: List[Scene]): ConnectionIO[List[Scene.WithRelated]] = {
    // "The astute among you will note that we don’t actually need a monad to do this;
    // an applicative functor is all we need here."
    // let's roll, doobie
    val componentsIO: ConnectionIO[
      (List[Image.WithRelated], List[Thumbnail], List[Datasource])] = {
      val thumbnails = getScenesThumbnails(scenes map { _.id })
      val images = getScenesImages(scenes map { _.id })
      val datasources = getScenesDatasources(scenes map { _.datasource })
      (images, thumbnails, datasources).tupled
    }

    componentsIO map {
      case (images, thumbnails, datasources) =>
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

  def sceneToSceneWithRelated(scene: Scene): ConnectionIO[Scene.WithRelated] = {
    val componentsIO
      : ConnectionIO[(List[Image.WithRelated], List[Thumbnail], Datasource)] = {
      val thumbnails = getScenesThumbnails(List(scene.id))
      val images = getScenesImages(List(scene.id))
      val datasource = DatasourceDao.unsafeGetDatasourceById(scene.datasource)
      (images, thumbnails, datasource).tupled
    }

    componentsIO map {
      case (images, thumbnails, datasource) =>
        scene.withRelatedFromComponents(images, thumbnails, datasource)
    }
  }

  def sceneOToSceneWithRelatedO(
      sceneO: Option[Scene]): ConnectionIO[Option[Scene.WithRelated]] = {
    sceneO match {
      case Some(scene) => sceneToSceneWithRelated(scene) map { _.some }
      case None        => Option.empty[Scene.WithRelated].pure[ConnectionIO]
    }
  }

  def getSceneQ(sceneId: UUID): Query0[Scene] = {
    (selectF ++ Fragments.whereAnd(fr"id = ${sceneId}"))
      .query[Scene]
  }

  def getScene(sceneId: UUID): ConnectionIO[Option[Scene.WithRelated]] = {
    val scenesO: ConnectionIO[Option[Scene]] = getSceneQ(sceneId).option
    scenesO map { _.toList } flatMap { scenesToScenesWithRelated } map {
      // guaranteed to be either 0 or 1 in the list based on .option above, so no need to worry
      // about losing information from lists with length > 1
      _.headOption
    }
  }

  @SuppressWarnings(Array("TraversableHead"))
  def unsafeGetScene(sceneId: UUID): ConnectionIO[Scene.WithRelated] = {
    val sceneIO: ConnectionIO[Scene] = getSceneQ(sceneId).unique
    // head is guaranteed to to succeed if the id is present, which is appropriate for a method marked
    // unsafe
    sceneIO flatMap { scene: Scene =>
      scenesToScenesWithRelated(List(scene))
    } map { _.head }
  }

  def getScenesToIngest(
      projectId: UUID): ConnectionIO[List[Scene.WithRelated]] = {
    val fragments = List(
      Some(
        fr"""(ingest_status = ${IngestStatus.Queued.toString} :: ingest_status
           OR (ingest_status = ${IngestStatus.Ingesting.toString} :: ingest_status AND (now() - modified_at) > '1 day'::interval)
           OR (ingest_status = ${IngestStatus.Failed.toString} :: ingest_status))
        """),
      Some(
        fr"scenes.id IN (SELECT scene_id FROM scenes_to_projects WHERE project_id = ${projectId})")
    )
    SceneDao.query.filter(fragments).list flatMap { scenes: List[Scene] =>
      scenesToScenesWithRelated(scenes)
    }
  }

  def makeFilters[T](myList: List[T])(
      implicit filterable: Filterable[Scene.WithRelated, T])
    : List[List[Option[Fragment]]] = {
    myList.map(filterable.toFilters(_))
  }

  def authQuery(
      user: User,
      objectType: ObjectType,
      ownershipTypeO: Option[String] = None,
      groupTypeO: Option[GroupType] = None,
      groupIdO: Option[UUID] = None): Dao.QueryBuilder[Scene.WithRelated] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Scene.WithRelated](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Scene.WithRelated](selectF,
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
