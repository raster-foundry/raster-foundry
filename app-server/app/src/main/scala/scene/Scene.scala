package com.azavea.rf.scene

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import slick.lifted._

import com.azavea.rf.AkkaSystem
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.utils.{Database, PaginatedResponse}


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

  /** Insert a scene into the database
    *
    * @param scene ScenesRow
    */
  def insertScene(scene: ScenesRow)
    (implicit database: Database, ec: ExecutionContext): Future[Try[ScenesRow]] = {

    import database.driver.api._

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
    (implicit database: Database, ec: ExecutionContext): Future[Option[ScenesRow]] = {

    import database.driver.api._
    database.db.run {
      Scenes.filter(_.id === sceneId).result.headOption
    }
  }

  /** Use query parameters to request scenes
    *
    * @param sceneParams Scene Params
    */
  def filterScenes(sceneParams: SceneQueryParameters)
    (implicit database: Database, ec: ExecutionContext):SceneQuery = {

    import database.driver.api._

    // Extracts month from timestamp in database for queries
    val datePart = SimpleFunction.binary[String, Option[Timestamp], Int]("date_part")

    Scenes.filter { scene =>
      List(
        sceneParams.createdBy.map(scene.createdBy === _),
        sceneParams.modifiedBy.map(scene.modifiedBy === _),
        sceneParams.minCreateDatetime.map(scene.createdAt > _),
        sceneParams.maxCreateDatetime.map(scene.createdAt < _)
      ).collect({case Some(criteria)  => criteria})
        .reduceLeftOption(_ && _).getOrElse(true:Rep[Boolean])
    }.filter { scene =>
      // filters for option columns (have different return type) - acquisitionDate, cloudCover
      List(
        sceneParams.maxAcquisitionDatetime.map(scene.acquisitionDate < _),
        sceneParams.minAcquisitionDatetime.map(scene.acquisitionDate > _),
        sceneParams.maxCloudCover.map(scene.cloudCover < _),
        sceneParams.minCloudCover.map(scene.cloudCover > _),
        sceneParams.minSunAzimuth.map(scene.sunAzimuth > _),
        sceneParams.maxSunAzimuth.map(scene.sunAzimuth < _),
        sceneParams.minSunElevation.map(scene.sunElevation > _),
        sceneParams.maxSunElevation.map(scene.sunElevation < _)
      ).collect({case Some(criteria)  => criteria})
      .reduceLeftOption(_ && _).getOrElse(Some(true):Rep[Option[Boolean]])
    }.filter { scene =>
      // Months
      sceneParams.month.map(datePart("month", scene.acquisitionDate) === _)
        .reduceLeftOption(_ || _).getOrElse(true:Rep[Boolean])
    }.filter { scene =>
      // Organization
      sceneParams.organization.map(scene.organizationId === _)
        .reduceLeftOption(_ || _).getOrElse(true:Rep[Boolean])
    }.filter { scene =>
      // Datasources
      sceneParams.datasource.map(scene.datasource === _)
        .reduceLeftOption(_ || _).getOrElse(true:Rep[Boolean])
    }
  }

  /** Get scenes given a page request and query parameters
    *
    * @param page PageRequet request for pagination
    * @param sceneParams SceneParams parameters used for filtering
    */
  def getScenes(page: PageRequest, sceneParams: SceneQueryParameters)
    (implicit database: Database, ec: ExecutionContext): Future[PaginatedResponse[ScenesRow]] = {

    import database.driver.api._
    val query = filterScenes(sceneParams)
    val scenesQueryResult = database.db.run {
      val queryResult = query.result
      val action = applySort(query, page.sort)
        .drop(page.offset * page.limit)
        .take(page.limit)
        .result
      log.debug(s"Query for scenes -- SQL: ${action.statements.headOption}")
      action
    }
    val totalScenesQuery = database.db.run {
      val action = query.length.result
      log.debug(s"Total Query for scenes -- SQL: ${action.statements.headOption}")
      action
    }

    for {
      totalScenes <- totalScenesQuery
      scenes <- scenesQueryResult
    } yield {
      val hasNext = (page.offset + 1) * page.limit < totalScenes // 0 indexed page offset
      val hasPrevious = page.offset > 0
      PaginatedResponse(totalScenes, hasPrevious, hasNext,
        page.offset, page.limit, scenes)
    }
  }

  /** Apply sorting for query of scenes
    *
    * @param query SceneQuery query to apply sorting to (filters should be applied)
    * @param sortMap Map map to use for applying sort with
    */
  def applySort(query: SceneQuery, sortMap: Map[String, Order])
    (implicit database: Database, ec: ExecutionContext): SceneQuery = {

    import database.driver.api._

    // Extracts month from timestamp in database for queries
    val datePart = SimpleFunction.binary[String, Option[Timestamp], Int]("date_part")

    sortMap.headOption match {
      case Some(("createdAt", Order.Asc)) => applySort(query.sortBy(_.createdAt.asc),
        sortMap.tail)
      case Some(("createdAt", Order.Desc)) => applySort(query.sortBy(_.createdAt.desc),
        sortMap.tail)

      case Some(("modifiedAt", Order.Asc)) => applySort(query.sortBy(_.modifiedAt.asc),
        sortMap.tail)
      case Some(("modifiedAt", Order.Desc)) => applySort(query.sortBy(_.modifiedAt.desc),
        sortMap.tail)

      case Some(("organization", Order.Asc)) => applySort(query.sortBy(_.organizationId.asc),
        sortMap.tail)
      case Some(("organization", Order.Desc)) => applySort(query.sortBy(_.organizationId.desc),
        sortMap.tail)

      case Some(("datasource", Order.Asc)) => applySort(query.sortBy(_.datasource.asc),
        sortMap.tail)
      case Some(("datasource", Order.Desc)) => applySort(query.sortBy(_.datasource.desc),
        sortMap.tail)

      case Some(("month", Order.Asc)) => applySort(query.sortBy { scene =>
        datePart("month", scene.acquisitionDate).asc
      }, sortMap.tail)
      case Some(("month", Order.Desc)) => applySort(query.sortBy { scene =>
        datePart("month", scene.acquisitionDate).desc
      }, sortMap.tail)

      case Some(("acquisitionDatetime", Order.Asc)) => applySort(
        query.sortBy(_.acquisitionDate.asc), sortMap.tail)
      case Some(("acquisitionDatetime", Order.Desc)) => applySort(
        query.sortBy(_.acquisitionDate.desc), sortMap.tail)

      case Some(("sunAzimuth", Order.Asc)) => applySort(query.sortBy(_.sunAzimuth.asc),
        sortMap.tail)
      case Some(("sunAzimuth", Order.Desc)) => applySort(query.sortBy(_.sunAzimuth.desc),
        sortMap.tail)

      case Some(("sunElevation", Order.Asc)) => applySort(query.sortBy(_.sunElevation.asc),
        sortMap.tail)
      case Some(("sunElevation", Order.Desc)) => applySort(query.sortBy(_.sunElevation.desc),
        sortMap.tail)

      case Some(("cloudCover", Order.Asc)) => applySort(query.sortBy(_.cloudCover.asc),
        sortMap.tail)
      case Some(("cloudCover", Order.Desc)) => applySort(query.sortBy(_.cloudCover.desc),
        sortMap.tail)

      case Some(_) => applySort(query, sortMap.tail)
      case _ => query
    }
  }

  /** Delete a scene from the database
    *
    * @param sceneId java.util.UUID ID of scene to delete
    */
  def deleteScene(sceneId: java.util.UUID)(implicit database: Database, ec: ExecutionContext): Future[Int] = {
    import database.driver.api._
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
    (implicit database: Database, ec: ExecutionContext): Future[Try[Int]] = {

    import database.driver.api._

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
