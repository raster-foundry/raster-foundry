package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.{Scene, User, _}
import com.rasterfoundry.common.SceneToLayer

import cats.data._
import cats.implicits._
import com.rasterfoundry.datamodel.{Order, PageRequest}
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._

import java.util.UUID

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
      projectLayerShapeO <- sceneParams.sceneParams.projectLayerShape match {
        case Some(projectLayerId) =>
          ProjectLayerDao.query.filter(fr"id = ${projectLayerId}").selectOption
        case _ => None.pure[ConnectionIO]
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
          .filter(projectLayerShapeO map { _.geometry })
          .filter(sceneParams)
      }
      scenesPage <- sceneSearchBuilder.page(
        pageRequest,
        Map("acquisitionDatetime" -> Order.Desc) ++ pageRequest.sort,
        false
      )
      sceneBrowses <- scenesToSceneBrowse(
        scenesPage.results toList,
        sceneParams.sceneParams.project,
        sceneParams.sceneParams.layer
      )
      count <- sceneSearchBuilder.sceneCountIO(
        sceneParams.sceneSearchModeParams.exactCount)
    } yield {
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse[Scene.Browse](
        count,
        hasPrevious,
        scenesPage.hasNext,
        pageRequest.offset,
        pageRequest.limit,
        sceneBrowses.take(pageRequest.limit)
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
    (fr"""SELECT scenes.id, CASE WHEN pl.project_id IS NULL THEN false ELSE true END as in_project
             FROM scenes
             LEFT OUTER JOIN scenes_to_layers sl
             ON scenes.id = sl.scene_id
             JOIN project_layers pl
             ON sl.project_layer_id = pl.id""" ++
      Fragments.whereAnd(
        Fragments.in(fr"scenes.id", sceneIds),
        fr"pl.project_id = $projectId")).query[(UUID, Boolean)].to[List]

  def getScenesInLayer(sceneIds: NonEmptyList[UUID],
                       projectId: UUID,
                       layerId: UUID) =
    (fr"""SELECT scenes.id, CASE WHEN (sl.project_layer_id IS NULL OR pl.project_id != ${projectId}) THEN false ELSE true END as in_layer
             FROM scenes LEFT OUTER JOIN scenes_to_layers sl
             ON scenes.id = sl.scene_id
             JOIN project_layers pl
             ON sl.project_layer_id = pl.id
""" ++
      Fragments.whereAnd(
        Fragments.in(fr"scenes.id", sceneIds),
        fr"sl.project_layer_id = $layerId")).query[(UUID, Boolean)].to[List]

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

  def getScenesToLayers(
      sceneIds: List[UUID],
      layerId: UUID
  ): ConnectionIO[List[SceneToLayer]] =
    sceneIds.toNel match {
      case Some(ids) =>
        SceneToLayerDao.query
          .filter(Fragments.in(fr"scene_id", ids))
          .filter(fr"project_layer_id=$layerId")
          .list
      case _ =>
        List.empty[SceneToLayer].pure[ConnectionIO]
    }

  // We know the datasources list head exists because of the foreign key relationship
  @SuppressWarnings(Array("FilterDotHead", "TraversableHead"))
  def scenesToSceneBrowse(
      scenes: List[Scene],
      projectIdO: Option[UUID],
      layerIdO: Option[UUID]): ConnectionIO[List[Scene.Browse]] = {
    // "The astute among you will note that we don’t actually need a monad to do this;
    // an applicative functor is all we need here."
    // let's roll, doobie
    val componentsIO: ConnectionIO[(List[Thumbnail],
                                    List[Datasource],
                                    List[(UUID, Boolean)],
                                    List[(UUID, Boolean)])] = {
      val thumbnails = getScenesThumbnails(scenes map { _.id })
      val datasources = getScenesDatasources(scenes map { _.datasource })
      val inProjects = (scenes.toNel, projectIdO) match {
        case (Some(s), Some(p)) =>
          getScenesInProject(s map { _.id }, p)
        case _ => List.empty[(UUID, Boolean)].pure[ConnectionIO]
      }
      val inLayers = (scenes.toNel, projectIdO, layerIdO) match {
        case (Some(s), Some(p), Some(l)) =>
          getScenesInLayer(s map { _.id }, p, l)
        case _ => List.empty[(UUID, Boolean)].pure[ConnectionIO]
      }
      (thumbnails, datasources, inProjects, inLayers).tupled
    }

    componentsIO map {
      case (thumbnails, datasources, inProjects, inLayers) =>
        val groupedThumbs = thumbnails.groupBy(_.sceneId)
        scenes map { scene: Scene =>
          scene.browseFromComponents(
            groupedThumbs.getOrElse(scene.id, List.empty[Thumbnail]),
            datasources.filter(_.id == scene.datasource).head,
            inProjects.find(_._1 == scene.id).map(_._2),
            inLayers.find(_._1 == scene.id).map(_._2)
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

  def getScenesToIngest(projectLayerId: UUID): ConnectionIO[List[Scene]] = {
    val fragments = List(
      Some(
        fr"""(ingest_status = ${IngestStatus.Queued.toString} :: ingest_status
           OR (ingest_status = ${IngestStatus.Ingesting.toString} :: ingest_status AND (now() - modified_at) > '1 day'::interval)
           OR (ingest_status = ${IngestStatus.Failed.toString} :: ingest_status))
        """),
      Some(
        fr"scenes.id IN (SELECT scene_id FROM scenes_to_layers WHERE project_layer_id = ${projectLayerId})")
    )
    SceneDao.query.filter(fragments).list
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
