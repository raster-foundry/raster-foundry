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
  def insertThumbnail(thumbnail: Thumbnail): DBIO[Thumbnail] =
    (Thumbnails returning Thumbnails).forceInsert(thumbnail)

  /** Retrieve a single thumbnail from the database
    *
    * @param thumbnailId UUID ID Of thumbnail to query with
    */
  def getThumbnail(thumbnailId: UUID): DBIO[Option[Thumbnail]] =
    Thumbnails.filter(_.id === thumbnailId).result.headOption

  def listThumbnails(pageRequest: PageRequest, queryParams: ThumbnailQueryParameters)
                    (implicit database: DB): ListQueryResult[Thumbnail] = {

    val dropRecords = pageRequest.limit * pageRequest.offset
    val thumbnails = Thumbnails.filterBySceneParams(queryParams)
      .drop(dropRecords)
      .take(pageRequest.limit)
    ListQueryResult[Thumbnail](
      (thumbnails
         .drop(dropRecords)
         .take(pageRequest.limit)
         .result):DBIO[Seq[Thumbnail]],
      thumbnails.length.result
    )
  }

  /** Delete a scene from the database
    *
    * @param thumbnailId UUID ID of scene to delete
    */
  def deleteThumbnail(thumbnailId: UUID) =
    Thumbnails.filter(_.id === thumbnailId).delete

  /** Update a thumbnail in the database
    *
    * Allows updating the thumbnail from a user -- does not allow a user to update
    * createdBy or createdAt fields
    *
    * @param thumbnail Thumbnail scene to use to update the database
    * @param thumbnailId UUID ID of scene to update
    */
  def updateThumbnail(thumbnail: Thumbnail, thumbnailId: UUID) = {

    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateThumbnailsQuery = for {
      updateThumbnail <- Thumbnails.filter(_.id === thumbnailId)
    } yield (
      updateThumbnail.modifiedAt, updateThumbnail.widthPx, updateThumbnail.heightPx,
      updateThumbnail.thumbnailSize, updateThumbnail.scene, updateThumbnail.url
    )
    updateThumbnailsQuery.update(
      (
        updateTime, thumbnail.widthPx, thumbnail.heightPx,
        thumbnail.thumbnailSize, thumbnail.sceneId, thumbnail.url
      )
    )
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
