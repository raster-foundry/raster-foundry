package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields, UserFkFields, NameField, VisibilityField}
import com.azavea.rf.database.query.{UploadQueryParameters, ListQueryResult}
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.circe.Json

/** Table that represents uploads
  *
  * Uploads represent asynchronous upload tasks to import metadata and create scenes
  * from raw data
  */
class Uploads(_tableTag: Tag) extends Table[Upload](_tableTag, "uploads")
    with TimestampFields
    with OrganizationFkFields
{
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, owner,
    organizationId, uploadStatus, fileType, uploadType, files,
    datasource, metadata, visibility, projectId, source) <> (
    Upload.tupled, Upload.unapply
  )

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val uploadStatus: Rep[UploadStatus] = column[UploadStatus]("upload_status")
  val fileType: Rep[FileType] = column[FileType]("file_type")
  val uploadType: Rep[UploadType] = column[UploadType]("upload_type")
  val files: Rep[List[String]] = column[List[String]]("files")
  val datasource: Rep[java.util.UUID] = column[java.util.UUID]("datasource")
  val metadata: Rep[Json] = column[Json]("metadata", O.Length(2147483647,varying=false))
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val projectId: Rep[Option[java.util.UUID]] = column[Option[java.util.UUID]]("project_id", O.Default(None))
  val source: Rep[Option[String]] = column[Option[String]]("source", O.Default(None))

  lazy val organizationsFk = foreignKey("uploads_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("uploads_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("uploads_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val datasourceFk = foreignKey("scenes_datasource_fkey", datasource, Datasources)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val ownerUserFK = foreignKey("uploads_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val projectFk = foreignKey("upload_project_fkey", projectId, Projects)(r => r.id.?, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object Uploads extends TableQuery(tag => new Uploads(tag)) with LazyLogging {

  val tq = TableQuery[Uploads]
  type TableQuery = Query[Uploads, Upload, Seq]


  implicit class withUploadsTableQuery[M, U, C[_]](uploads: Uploads.TableQuery) extends
    UploadTableQuery[M, U, C](uploads)

  /** List uploads given a page request
    *
    * @param offset Int offset of request for pagination
    * @param limit Int limit of objects per page
    * @param queryParams UploadQueryparameters query parameters for request
    */
  def listUploads(offset: Int, limit: Int, queryParams: UploadQueryParameters, user: User) = {

    val dropRecords = limit * offset
    val accessibleUploads = Uploads.filterToSharedOrganizationIfNotInRoot(user)
    ListQueryResult[Upload](
      (accessibleUploads
         .filterByUploadParams(queryParams)
         .drop(dropRecords)
         .take(limit)
         .result):DBIO[Seq[Upload]],
      accessibleUploads.length.result
    )
  }

  /** Insert a upload given a create case class with a user
    *
    * @param uploadToCreate Upload.Create object to use to create full upload
    * @param user               User to create a new upload with
    */
  def insertUpload(uploadToCreate: Upload.Create, user: User) = {
    val upload = uploadToCreate.toUpload(user)
    (Uploads returning Uploads).forceInsert(upload)
  }


  /** Given a upload ID, attempt to retrieve it from the database
    *
    * @param uploadId UUID ID of upload to get from database
    * @param user     Results will be limited to user's organization
    */
  def getUpload(uploadId: UUID, user: User) =
    Uploads
      .filterToSharedOrganizationIfNotInRoot(user)
      .filter(_.id === uploadId)
      .result
      .headOption

  /** Given a upload ID, attempt to remove it from the database
    *
    * @param uploadId UUID ID of upload to remove
    * @param user     Results will be limited to user's organization
    */
  def deleteUpload(uploadId: UUID, user: User) =
    Uploads
      .filterToSharedOrganizationIfNotInRoot(user)
      .filter(_.id === uploadId)
      .delete

/** Update a upload @param upload Upload to use for update
    * @param uploadId UUID of upload to update
    * @param user User to use to update upload
    */
  def updateUpload(upload: Upload, uploadId: UUID, user: User) = {
    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateUploadQuery = for {
      updateUpload <- Uploads
                        .filterToSharedOrganizationIfNotInRoot(user)
                        .filter(_.id === uploadId)
    } yield (
      updateUpload.modifiedAt,
      updateUpload.modifiedBy,
      updateUpload.organizationId,
      updateUpload.files,
      updateUpload.datasource,
      updateUpload.uploadType,
      updateUpload.uploadStatus,
      updateUpload.metadata,
      updateUpload.visibility
    )

    updateUploadQuery.update(
      updateTime,
      user.id,
      upload.organizationId,
      upload.files,
      upload.datasource,
      upload.uploadType,
      upload.uploadStatus,
      upload.metadata,
      upload.visibility
    )
  }
}

class UploadTableQuery[M, U, C[_]](uploads: Uploads.TableQuery) {

  def filterByUploadParams(queryParams: UploadQueryParameters): Uploads.TableQuery = {
    val filteredByOrg = queryParams.organization match {
      case Some(org) => uploads.filter(_.organizationId === org)
      case _ => uploads
    }

    val filteredBySource = queryParams.datasource match {
      case Some(ds) => filteredByOrg.filter(_.datasource === ds)
      case _ => filteredByOrg
    }

    queryParams.uploadStatus match {
      case Some(st) => filteredBySource.filter(_.uploadStatus === UploadStatus.fromString(st))
      case _ => filteredBySource
    }
  }

  def page(pageRequest: PageRequest): Uploads.TableQuery = {
    Uploads
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}
