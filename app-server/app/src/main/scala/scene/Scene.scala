package com.azavea.rf.scene

import java.sql.Timestamp

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.Database


/** Handles interaction between scenes and database */
trait Scene {

  implicit val database:Database
  implicit val ec:ExecutionContext

  import database.driver.api._

  /** Insert a scene into the database
    *
    * @param scene ScenesRow
    */
  def insertScene(scene: ScenesRow): Future[Try[ScenesRow]] = {
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
  def getScene(sceneId: java.util.UUID): Future[Option[ScenesRow]] = {
    database.db.run {
      Scenes.filter(_.id === sceneId).result.headOption
    }
  }

  /** Delete a scene from the database
    *
    * @param sceneId java.util.UUID ID of scene to delete
    */
  def deleteScene(sceneId: java.util.UUID): Future[Try[Int]] = {
    database.db.run {
      Scenes.filter(_.id === sceneId).delete.asTry
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

  /** Update a scene in the database
    *
    * Allows updating the scene from a user -- does not allow a user to update
    * createdBy or createdAt fields
    *
    * @param scene ScenesRow scene to use to update the database
    * @param sceneId java.util.UUID ID of scene to update
    * @param user UsersRow user performing the update
    */
  def updateScene(scene: ScenesRow, sceneId: java.util.UUID, user: UsersRow): Future[Try[Int]] = {
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

