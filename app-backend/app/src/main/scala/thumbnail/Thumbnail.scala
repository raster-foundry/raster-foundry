package com.azavea.rf.thumbnail

import java.sql.Timestamp
import java.util.UUID

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure, Try}

import slick.lifted._
import com.lonelyplanet.akka.http.extensions.PageRequest

import com.azavea.rf.AkkaSystem
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.utils.{Database => DB, PaginatedResponse}

case class CreateThumbnail(
  organizationId: UUID,
  widthPx: Int,
  heightPx: Int,
  thumbnailSize: ThumbnailSize,
  sceneId: UUID,
  url: String
) {
  def toThumbnail: ThumbnailsRow = {
    val now = new Timestamp((new java.util.Date()).getTime())
    ThumbnailsRow(
      UUID.randomUUID, // primary key
      now, // created at,
      now, // modified at,
      organizationId,
      widthPx, // width in pixels
      heightPx, // height in pixels
      sceneId,
      url,
      thumbnailSize
    )
  }
}


object ThumbnailService extends AkkaSystem.LoggerExecutor {

  type ThumbnailQuery = Query[Thumbnails, Thumbnails#TableElementType, Seq]
  import ThumbnailFilters._

  import ExtendedPostgresDriver.api._

  /** Insert a thumbnail into the database
    *
    * @param thumbnail ThumbnailsRow
    */
  def insertThumbnail(thumbnail: ThumbnailsRow)
    (implicit database: DB, ec: ExecutionContext): Future[Try[ThumbnailsRow]] = {

    val action = Thumbnails.forceInsert(thumbnail)
    log.debug(s"Inserting thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.asTry
    } map {
      case Success(_) => Success(thumbnail)
      case Failure(e) => throw e
    }
  }

  /** Retrieve a single thumbnail from the database
    *
    * @param thumbnailId UUID ID Of thumbnail to query with
    */
  def getThumbnail(thumbnailId: UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Option[ThumbnailsRow]] = {

    val action = Thumbnails.filter(_.id === thumbnailId).result
    log.debug(s"Retrieving thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.headOption
    }
  }

  def getThumbnails(pageRequest: PageRequest, queryParams: ThumbnailQueryParameters)
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[ThumbnailsRow]] = {

    val thumbnails = Thumbnails.filterBySceneParams(queryParams)

    val paginatedThumbnails = database.db.run {
      val action = thumbnails.page(pageRequest).result
      log.debug(s"Query for thumbnails -- SQL ${action.statements.headOption}")
      action
    }

    val totalThumbnailsQuery = database.db.run { thumbnails.length.result }

    for {
      totalThumbnails <- totalThumbnailsQuery
      thumbnails <- paginatedThumbnails
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalThumbnails
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse[ThumbnailsRow](totalThumbnails, hasPrevious, hasNext,
        pageRequest.offset, pageRequest.limit, thumbnails)
    }
  }

  /** Delete a scene from the database
    *
    * @param thumbnailId UUID ID of scene to delete
    */
  def deleteThumbnail(thumbnailId: UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Try[Int]] = {

    val action = Thumbnails.filter(_.id === thumbnailId).delete
    log.debug(s"Deleting thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.asTry
    } map {
      case Success(result) => {
        result match {
          case 1 => Success(1)
          case 0 => Success(0)
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
    * @param thumbnailId UUID ID of scene to update
    * @param user UsersRow user performing the update
    */
  def updateThumbnail(thumbnail: ThumbnailsRow, thumbnailId: UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Try[Int]] = {

    val updateTime = new Timestamp((new java.util.Date()).getTime())

    val updateThumbnailQuery = for {
      updateThumbnail <- Thumbnails.filter(_.id === thumbnailId)
    } yield (
      updateThumbnail.modifiedAt, updateThumbnail.widthPx, updateThumbnail.heightPx,
      updateThumbnail.thumbnailSize, updateThumbnail.scene, updateThumbnail.url
    )
    database.db.run {
      updateThumbnailQuery.update((
        updateTime, thumbnail.widthPx, thumbnail.heightPx,
        thumbnail.thumbnailSize, thumbnail.scene, thumbnail.url
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
