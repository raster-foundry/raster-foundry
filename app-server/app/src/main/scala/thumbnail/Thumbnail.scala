package com.azavea.rf.thumbnail

import java.sql.Timestamp

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure, Try}

import com.azavea.rf.AkkaSystem
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.Database

trait Thumbnail extends AkkaSystem.LoggerExecutor {

  implicit val ec:ExecutionContext
  implicit val database:Database

  import database.driver.api._

  /** Insert a thumbnail into the database
    *
    * @param thumbnail ThumbnailsRow
    */
  def insertThumbnail(thumbnail: ThumbnailsRow): Future[Try[ThumbnailsRow]] = {
    lazy val action = Thumbnails.forceInsert(thumbnail)
    log.debug(s"Inserting thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.asTry
    } map {
      case Success(_) => Success(thumbnail)
      case Failure(e) => Failure(e)
    }
  }

  /** Retrieve a single thumbnail from the database
    *
    * @param thumbnailId java.util.UUID ID Of thumbnail to query with
    */
  def getThumbnail(thumbnailId: java.util.UUID): Future[Option[ThumbnailsRow]] = {
    val action = Thumbnails.filter(_.id === thumbnailId).result
    log.debug(s"Retrieving thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.headOption
    }
  }

  /** Delete a scene from the database
    *
    * @param thumbnailId java.util.UUID ID of scene to delete
    */
  def deleteThumbnail(thumbnailId: java.util.UUID): Future[Try[Int]] = {
    val action = Thumbnails.filter(_.id === thumbnailId).delete
    log.debug(s"Deleting thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.asTry
    } map {
      case Success(result) => {
        result match {
          case 1 => Success(1)
          case _ => Failure(new Exception("Error while updating thumbnail"))
        }
      }
      case Failure(e) => Failure(e)
    }
  }

  /** Update a thumbnail in the database
    *
    * Allows updating the thumbnail from a user -- does not allow a user to update
    * createdBy or createdAt fields
    *
    * @param thumbnail ThumbnailsRow scene to use to update the database
    * @param thumbnailId java.util.UUID ID of scene to update
    * @param user UsersRow user performing the update
    */
  def updateThumbnail(thumbnail: ThumbnailsRow, thumbnailId: java.util.UUID): Future[Try[Int]] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime())

    val updateThumbnailQuery = for {
      updateThumbnail <- Thumbnails.filter(_.id === thumbnailId)
    } yield (
      updateThumbnail.modifiedAt, updateThumbnail.widthPx, updateThumbnail.heightPx,
      updateThumbnail.size, updateThumbnail.scene, updateThumbnail.url
    )
    database.db.run {
      updateThumbnailQuery.update((
        updateTime, thumbnail.widthPx, thumbnail.heightPx,
        thumbnail.size, thumbnail.scene, thumbnail.url
      )).asTry
    } map {
      case Success(result) => {
        result match {
          case 1 => Success(1)
          case _ => Failure(new Exception("Error while updating thumbnail"))
        }
      }
      case Failure(e) => Failure(e)
    }
  }
}
