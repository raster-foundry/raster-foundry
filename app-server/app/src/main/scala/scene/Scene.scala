package com.azavea.rf.scene

import java.sql.Timestamp
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.lonelyplanet.akka.http.extensions.PageRequest
import slick.lifted._

import com.azavea.rf.AkkaSystem
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.utils.{Database => DB, PaginatedResponse}
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver


/** Handles interaction between scenes and database */
object SceneService extends AkkaSystem.LoggerExecutor {

  // Type alias to make formatting better, return types clearer
  type SceneQuery = Query[Scenes, Scenes#TableElementType, Seq]

  // Implicit class that adds common filters
  import SceneFilters._

  import ExtendedPostgresDriver.api._

  /** Insert a scene into the database
    *
    * @param scene CreateScene scene to create
    * @param user UsersRow user creating scene
    *
    * This implementation allows a user to post a scene with thumbnails, footprint, and
    * images which are all created in a single transaction
    */
  def insertScene(createScene: CreateScene, user: UsersRow)
    (implicit database: DB, ec: ExecutionContext): Future[Try[SceneWithRelated]] = {

    val scene = createScene.toScene(user.id)
    val scenesRowInsert = Scenes.forceInsert(scene)

    val thumbnails = createScene.thumbnails.map(_.toThumbnailsRow(user.id, scene))
    val thumbnailsInsert = DBIO.seq(thumbnails.map(Thumbnails.forceInsert): _*)

    val images = createScene.images.map(_.toImagesRow(user.id, scene))
    val imagesInsert = DBIO.seq(images.map(Images.forceInsert): _*)

    val footprint = createScene.footprint.map(_.toFootprintsRow(user.id, scene))
    val footprintInsert = footprint
      .map(Footprints.forceInsert)
      .fold(DBIO.successful(Option.empty[Int]): DBIOAction[Option[Int], NoStream, Effect.Write])(_.map(Some(_)))

    val sceneInsert = (for {
      _ <- scenesRowInsert
      _ <- thumbnailsInsert
      _ <- footprintInsert
      _ <- imagesInsert
    } yield ()).transactionally

    database.db.run {
      sceneInsert.asTry
    } map {
      case Success(_) => Success(
        SceneWithRelated.fromComponents(scene, footprint, images, thumbnails)
      )
      case Failure(e) => {
        log.error(e.toString)
        Failure(e)
      }
    }
  }

  /** Helper function to create Iterable[SceneWithRelated] from join
    *
    * It is necessary to map over the distinct scenes because that is the only way to
    * ensure that the sort order of the query result remains ordered after grouping
    *
    * @param joinResult result of join query to return scene with related
    * information
    */
  def createScenesWithRelated(joinResult: Seq[(ScenesRow, Option[FootprintsRow], Option[ImagesRow],
    Option[ThumbnailsRow])]): Iterable[SceneWithRelated] = {

    val distinctScenes = joinResult.map(_._1).distinct
    val grouped = joinResult.groupBy(_._1)
    distinctScenes.map{scene =>
      // This should be relatively safe since scene is the key grouped by
      val (seqFootprint, seqImages, seqThumbnails) = grouped(scene)
        .map{ case (sr, fp, im, th) => (fp, im, th)}
        .unzip3
      SceneWithRelated.fromComponents(
        scene, seqFootprint.flatten.headOption, seqImages.flatten, seqThumbnails.flatten.distinct
      )
    }
  }

  /** Retrieve a single scene from the database
    *
    * @param sceneId java.util.UUID ID of scene to query with
    */
  def getScene(sceneId: java.util.UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Option[SceneWithRelated]] = {
    val sceneJoinQuery = Scenes
      .filter(_.id === sceneId)
      .joinWithRelated

    database.db.run {
      val action = sceneJoinQuery.result
      log.debug(s"Total Query for scenes -- SQL: ${action.statements.headOption}")
      action
    } map { join =>
      val scenesWithRelated = createScenesWithRelated(join)
      scenesWithRelated.headOption
    }
  }

  /** Get scenes given a page request and query parameters
    *
    * @param pageRequest PageRequet request for pagination
    * @param sceneParams SceneParams parameters used for filtering
    */
  def getScenes(pageRequest: PageRequest, combinedParams: CombinedSceneQueryParams)
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[SceneWithRelated]] = {

    val pagedScenes = Scenes
      .joinWithRelated
      .page(combinedParams, pageRequest)

    val scenesQueryResult = database.db.run {
      val action = pagedScenes.result
      log.debug(s"Paginated Query for scenes -- SQL: ${action.statements.headOption}")
      action
    } map(join => createScenesWithRelated(join))

    val totalScenesQueryResult = database.db.run {
      val action = Scenes
        .filterByOrganization(combinedParams.orgParams)
        .filterByUser(combinedParams.userParams)
        .filterByTimestamp(combinedParams.timestampParams)
        .filterBySceneParams(combinedParams.sceneParams)
        .length
        .result
      log.debug(s"Total Query for scenes -- SQL: ${action.statements.headOption}")
      action
    }

    for {
      totalScenes <- totalScenesQueryResult
      scenes <- scenesQueryResult
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalScenes // 0 indexed page offset
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse(totalScenes, hasPrevious, hasNext,
        pageRequest.offset, pageRequest.limit, scenes.toSeq)
    }
  }


  /** Delete a scene from the database
    *
    * @param sceneId java.util.UUID ID of scene to delete
    */
  def deleteScene(sceneId: java.util.UUID)(implicit database: DB, ec: ExecutionContext): Future[Int] = {

    database.db.run {
      Scenes.filter(_.id === sceneId).delete
    }
  }

  /** Update a scene in the database
    *
    * Allows updating the scene from a user -- does not allow a user to update
    * createdBy or createdAt fields
    *
    * @param scene ScenesRow scene to use to update the database
    * @param sceneId java.util.UUID ID of scene to update
    * @param user UsersRow user performing the update
    */
  def updateScene(scene: ScenesRow, sceneId: UUID, user: UsersRow)
    (implicit database: DB, ec: ExecutionContext): Future[Try[Int]] = {

    val updateTime = new Timestamp((new java.util.Date()).getTime())

    val updateSceneQuery = for {
      updateScene <- Scenes.filter(_.id === sceneId)
    } yield (
      updateScene.modifiedAt, updateScene.modifiedBy, updateScene.ingestSizeBytes,
      updateScene.resolutionMeters, updateScene.datasource, updateScene.cloudCover,
      updateScene.acquisitionDate, updateScene.tags, updateScene.sceneMetadata,
      updateScene.thumbnailStatus, updateScene.boundaryStatus, updateScene.status, updateScene.name
    )
    database.db.run {
      updateSceneQuery.update((
        updateTime, user.id, scene.ingestSizeBytes, scene.resolutionMeters,
        scene.datasource, scene.cloudCover, scene.acquisitionDate, scene.tags, scene.sceneMetadata,
        scene.thumbnailStatus, scene.boundaryStatus, scene.status, scene.name
      )).asTry
    } map {
      case Success(result) => {
        result match {
          case 1 => Success(1)
          case _ => Failure(new Exception("Error while updating scene"))
        }
      }
      case Failure(e) => Failure(e)
    }
  }
}
