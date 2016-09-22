package com.azavea.rf.scene

import java.sql.Timestamp
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import slick.lifted._

import com.azavea.rf.AkkaSystem
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.utils.{Database => DB, PaginatedResponse}
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver


case class CreateScene(
  organizationId: UUID,
  ingestSizeBytes: Int,
  visibility: Visibility,
  resolutionMeters: Float,
  tags: List[String],
  datasource: String,
  sceneMetadata: Map[String, Any],
  cloudCover: Option[Float],
  acquisitionDate: Option[java.sql.Timestamp],
  thumbnailStatus: JobStatus,
  boundaryStatus: JobStatus,
  status: JobStatus,
  sunAzimuth: Option[Float],
  sunElevation: Option[Float]
) {
  def toScene(userId: String): ScenesRow = {
    val now = new Timestamp((new java.util.Date()).getTime())
    ScenesRow(
      UUID.randomUUID, // primary key
      now, // createdAt
      now, // modifiedAt
      organizationId,
      userId, // createdBy
      userId, // modifiedBy
      ingestSizeBytes,
      visibility,
      resolutionMeters,
      tags,
      datasource,
      sceneMetadata,
      cloudCover,
      acquisitionDate,
      thumbnailStatus,
      boundaryStatus,
      status,
      sunAzimuth,
      sunElevation
    )
  }
}


/** Handles interaction between scenes and database */
object SceneService extends AkkaSystem.LoggerExecutor {

  // Type alias to make formatting better, return types clearer
  type SceneQuery = Query[Scenes, Scenes#TableElementType, Seq]

  // Implicit class that adds common filters
  import SceneFilters._

  import ExtendedPostgresDriver.api._

  /** Insert a scene into the database
    *
    * @param scene ScenesRow
    */
  def insertScene(scene: ScenesRow)
    (implicit database: DB, ec: ExecutionContext): Future[Try[ScenesRow]] = {

    database.db.run {
      Scenes.forceInsert(scene).asTry
    } map {
      case Success(_) => Success(scene)
      case Failure(e) => Failure(e)
    }
  }

  /** Retrieve a single scene from the database
    *
    * @param sceneId java.util.UUID ID of scene to query with
    */
  def getScene(sceneId: java.util.UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Option[ScenesRow]] = {

    database.db.run {
      Scenes.filter(_.id === sceneId).result.headOption
    }
  }

  /** Get scenes given a page request and query parameters
    *
    * @param pageRequest PageRequet request for pagination
    * @param sceneParams SceneParams parameters used for filtering
    */
  def getScenes(pageRequest: PageRequest, combinedParams: CombinedSceneQueryParams)
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[ScenesRow]] = {

    val scenes = Scenes.filterByOrganization(combinedParams.orgParams)
      .filterByUser(combinedParams.userParams)
      .filterByTimestamp(combinedParams.timestampParams)
      .filterBySceneParams(combinedParams.sceneParams)

    val scenesQueryResult = database.db.run {
      val action = scenes.page(pageRequest).result
      log.debug(s"Query for scenes -- SQL: ${action.statements.headOption}")
      action
    }
    val totalScenesQuery = database.db.run {
      val action = scenes.length.result
      log.debug(s"Total Query for scenes -- SQL: ${action.statements.headOption}")
      action
    }

    for {
      totalScenes <- totalScenesQuery
      scenes <- scenesQueryResult
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalScenes // 0 indexed page offset
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse(totalScenes, hasPrevious, hasNext,
        pageRequest.offset, pageRequest.limit, scenes)
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
      updateScene.thumbnailStatus, updateScene.boundaryStatus, updateScene.status
    )
    database.db.run {
      updateSceneQuery.update((
        updateTime, user.id, scene.ingestSizeBytes, scene.resolutionMeters,
        scene.datasource, scene.cloudCover, scene.acquisitionDate, scene.tags, scene.sceneMetadata,
        scene.thumbnailStatus, scene.boundaryStatus, scene.status
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
