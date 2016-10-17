package com.azavea.rf.database.tables

import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields}
import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import java.util.UUID
import java.sql.Timestamp
import com.typesafe.scalalogging.LazyLogging
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}

/** Table description of table thumbnails. Objects of this class serve as prototypes for rows in queries. */
class Thumbnails(_tableTag: Tag) extends Table[Thumbnail](_tableTag, "thumbnails")
                                         with OrganizationFkFields
                                         with TimestampFields
{
  def * = (id, createdAt, modifiedAt, organizationId, widthPx, heightPx, scene, url, thumbnailSize) <> (Thumbnail.tupled, Thumbnail.unapply _)
  /** Maps whole row to an option. Useful for outer joins. */
  def ? = (Rep.Some(id), Rep.Some(createdAt), Rep.Some(modifiedAt), Rep.Some(organizationId), Rep.Some(widthPx), Rep.Some(heightPx), Rep.Some(scene), Rep.Some(url), Rep.Some(thumbnailSize)).shaped.<>({r=>import r._; _1.map(_=> Thumbnail.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
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
  type TableQuery = Query[Thumbnails, Thumbnails#TableElementType, Seq]

  implicit class withThumbnailsQuery[M, U, C[_]](thumbnails: Thumbnails.TableQuery) extends
      ThumbnailDefaultQuery[M, U, C](thumbnails)

  /** Insert a thumbnail into the database
    *
    * @param thumbnail Thumbnail
    */
  def insertThumbnail(thumbnail: Thumbnail)
    (implicit database: DB, ec: ExecutionContext): Future[Try[Thumbnail]] = {

    val action = Thumbnails.forceInsert(thumbnail)
    logger.debug(s"Inserting thumbnail with: ${action.statements.headOption}")
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
    (implicit database: DB, ec: ExecutionContext): Future[Option[Thumbnail]] = {

    val action = Thumbnails.filter(_.id === thumbnailId).result
    logger.debug(s"Retrieving thumbnail with: ${action.statements.headOption}")
    database.db.run {
      action.headOption
    }
  }

  def getThumbnails(pageRequest: PageRequest, queryParams: ThumbnailQueryParameters)
    (implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[Thumbnail]] = {

    val thumbnails = Thumbnails.filterBySceneParams(queryParams)

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
    */
  def deleteThumbnail(thumbnailId: UUID)
    (implicit database: DB, ec: ExecutionContext): Future[Try[Int]] = {

    val action = Thumbnails.filter(_.id === thumbnailId).delete
    logger.debug(s"Deleting thumbnail with: ${action.statements.headOption}")
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
    * @param thumbnail Thumbnail scene to use to update the database
    * @param thumbnailId UUID ID of scene to update
    * @param user User user performing the update
    */
  def updateThumbnail(thumbnail: Thumbnail, thumbnailId: UUID)
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
        thumbnail.thumbnailSize, thumbnail.sceneId, thumbnail.url
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

class ThumbnailDefaultQuery[M, U, C[_]](thumbnails: Thumbnails.TableQuery) {

  def filterBySceneParams(sceneParams: ThumbnailQueryParameters): Thumbnails.TableQuery = {
    thumbnails.filter(_.scene === sceneParams.sceneId)
  }

  def sort(sortMap: Map[String, Order]): Thumbnails.TableQuery = {
    def applySort(query: Thumbnails.TableQuery, sortMap: Map[String, Order]): Thumbnails.TableQuery = {
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

        case Some(_) => applySort(query, sortMap.tail)
        case _ => query
      }
    }
    applySort(thumbnails, sortMap)
  }

  def page(pageRequest: PageRequest): Thumbnails.TableQuery = {
    val sorted = thumbnails.sort(pageRequest.sort)
    sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
  }
}
