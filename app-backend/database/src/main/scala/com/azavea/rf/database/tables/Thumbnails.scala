package com.azavea.rf.database.tables

import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, VisibilityField}
import com.azavea.rf.database.query._
import com.azavea.rf.database.sort._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import java.util.UUID
import java.sql.Timestamp

import com.typesafe.scalalogging.LazyLogging
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Table description of table thumbnails. Objects of this class serve as prototypes for rows in queries. */
class Thumbnails(_tableTag: Tag) extends Table[Thumbnail](_tableTag, "thumbnails")
                                         with OrganizationFkFields
                                         with TimestampFields
                                         with VisibilityField
{
  def * = (id, createdAt, modifiedAt, organizationId, widthPx, heightPx, scene, url, thumbnailSize) <> (Thumbnail.tupled, Thumbnail.unapply _)

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val widthPx: Rep[Int] = column[Int]("width_px")
  val heightPx: Rep[Int] = column[Int]("height_px")
  val scene: Rep[java.util.UUID] = column[java.util.UUID]("scene")
  val url: Rep[String] = column[String]("url", O.Length(255,varying=true))
  val thumbnailSize: Rep[ThumbnailSize] = column[ThumbnailSize]("thumbnail_size")

  /** Foreign key referencing Organizations (database name thumbnails_organization_id_fkey) */
  lazy val organizationsFk = foreignKey("thumbnails_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  /** Foreign key referencing Scenes (database name thumbnails_scene_fkey) */
  lazy val scenesFk = foreignKey("thumbnails_scene_fkey", scene, Scenes)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

/** Collection-like TableQuery object for table Thumbnails */
object Thumbnails extends TableQuery(tag => new Thumbnails(tag)) with LazyLogging {
  type TableQuery = Query[Thumbnails, Thumbnail, Seq]

  implicit val projectsSorter: QuerySorter[Thumbnails] =
    new QuerySorter(
      new OrganizationFkSort(identity[Thumbnails]),
      new TimestampSort(identity[Thumbnails]))

  implicit class withThumbnailsQuery[M, U, C[_]](thumbnails: Thumbnails.TableQuery) extends
      ThumbnailDefaultQuery[M, U, C](thumbnails)

  /** Insert a thumbnail into the database
    *
    * @param thumbnail Thumbnail
    */
  def insertThumbnail(thumbnail: Thumbnail)
                     (implicit database: DB): Future[Thumbnail] = {

    val action = Thumbnails.forceInsert(thumbnail)
    logger.debug(s"Inserting thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.map( _ => thumbnail)
    }
  }

  /** Retrieve a single thumbnail from the database
    *
    * @param thumbnailId UUID ID Of thumbnail to query with
    * @param user        Results will be limited to user's organization
    */
  def getThumbnail(thumbnailId: UUID, user: User)
                  (implicit database: DB): Future[Option[Thumbnail]] = {

    val action = Thumbnails
                   .filterToSharedOrganizationIfNotInRoot(user)
                   .filter(_.id === thumbnailId)
                   .result
    logger.debug(s"Retrieving thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.headOption
    }
  }

  def listThumbnails(pageRequest: PageRequest, queryParams: ThumbnailQueryParameters, user: User)
                    (implicit database: DB): Future[PaginatedResponse[Thumbnail]] = {

    val thumbnails = Thumbnails
                       .filterToSharedOrganizationIfNotInRoot(user)
                       .filterBySceneParams(queryParams)

    val paginatedThumbnails = database.db.run {
      val action = thumbnails.page(pageRequest).result
      logger.debug(s"Query for thumbnails -- SQL ${action.statements.headOption}")
      action
    }

    val totalThumbnailsQuery = database.db.run { thumbnails.length.result }

    for {
      totalThumbnails <- totalThumbnailsQuery
      thumbnails <- paginatedThumbnails
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalThumbnails
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse[Thumbnail](totalThumbnails, hasPrevious, hasNext,
        pageRequest.offset, pageRequest.limit, thumbnails)
    }
  }

  /** Delete a scene from the database
    *
    * @param thumbnailId UUID ID of scene to delete
    * @param user        Results will be limited to user's organization
    */
  def deleteThumbnail(thumbnailId: UUID, user: User)
                     (implicit database: DB): Future[Int] = {

    val action = Thumbnails
                   .filterToSharedOrganizationIfNotInRoot(user)
                   .filter(_.id === thumbnailId)
                   .delete
    logger.debug(s"Deleting thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.map {
        case 1 => 1
        case 0 => 0
        case c => throw new IllegalStateException(s"Error deleting thumbnail: update result expected to be 1, was $c")
      }
    }
  }

  /** Update a thumbnail in the database
    *
    * Allows updating the thumbnail from a user -- does not allow a user to update
    * createdBy or createdAt fields
    *
    * @param thumbnail Thumbnail scene to use to update the database
    * @param thumbnailId UUID ID of scene to update
    */
  def updateThumbnail(thumbnail: Thumbnail, thumbnailId: UUID, user: User)
                     (implicit database: DB): Future[Int] = {

    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateThumbnailQuery = for {
      updateThumbnail <- Thumbnails
                           .filterToSharedOrganizationIfNotInRoot(user)
                           .filter(_.id === thumbnailId)
    } yield (
      updateThumbnail.modifiedAt, updateThumbnail.widthPx, updateThumbnail.heightPx,
      updateThumbnail.thumbnailSize, updateThumbnail.scene, updateThumbnail.url
    )
    database.db.run {
      updateThumbnailQuery.update((
        updateTime, thumbnail.widthPx, thumbnail.heightPx,
        thumbnail.thumbnailSize, thumbnail.sceneId, thumbnail.url
      )).map {
        case 1 => 1
        case c => throw new IllegalStateException(s"Error updating thumbnail: update result expected to be 1, was $c")
      }
    }
  }
}

class ThumbnailDefaultQuery[M, U, C[_]](thumbnails: Thumbnails.TableQuery) {

  def filterBySceneParams(sceneParams: ThumbnailQueryParameters): Thumbnails.TableQuery = {
    thumbnails.filter(_.scene === sceneParams.sceneId)
  }

  def page(pageRequest: PageRequest): Thumbnails.TableQuery = {
    val sorted = thumbnails.sort(pageRequest.sort)
    sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
  }
}
