package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.notification._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object GeojsonUploadDao extends Dao[GeojsonUpload] {

  val tableName = "geojson_uploads"

  val selectF = sql"""
    SELECT
       id, created_at, created_by, modified_at,
       upload_status, file_type, upload_type,
       files, project_id, project_layer_id, annotation_group, keep_files
    FROM
  """ ++ tableF

  def getUploadById(uploadId: UUID): ConnectionIO[Option[GeojsonUpload]] =
    query.filter(uploadId).selectOption

  def unsafeGetUploadById(uploadId: UUID): ConnectionIO[GeojsonUpload] =
    query.filter(uploadId).select

  def insert(
      newUpload: GeojsonUpload.Create,
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID,
      user: User
  ): ConnectionIO[GeojsonUpload] = {
    val upload = newUpload.toGeojsonUpload(
      user,
      projectId,
      layerId,
      annotationGroupId
    )
    sql"""
       INSERT INTO geojson_uploads
         (id, created_at, created_by, modified_at,
          upload_status, file_type, upload_type,
          files, project_id, project_layer_id, annotation_group,
          keep_files)
       VALUES (
         ${upload.id}, ${upload.createdAt}, ${upload.createdBy}, ${upload.modifiedAt},
         ${upload.uploadStatus}, ${upload.fileType}, ${upload.uploadType},
         ${upload.files}, ${upload.projectId}, ${upload.projectLayerId}, ${upload.annotationGroup},
         ${upload.keepFiles}
       )
      """.update.withUniqueGeneratedKeys[GeojsonUpload](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "upload_status",
      "file_type",
      "upload_type",
      "files",
      "project_id",
      "project_layer_id",
      "annotation_group",
      "keep_files"
    )
  }

  def update(
      upload: GeojsonUpload,
      projectId: UUID,
      projectLayerId: UUID,
      annotationGroupId: UUID,
      id: UUID,
      user: User
  ): ConnectionIO[Int] = {
    val idFilter =
      fr"id = ${id} AND project_id = $projectId AND project_layer_id = $projectLayerId AND annotation_group = $annotationGroupId"
    val oldUploadIO = unsafeGetUploadById(id)
    val recordUpdateIO = (sql"""
       UPDATE geojson_uploads
       SET
          modified_at = NOW(),
          upload_status = ${upload.uploadStatus}
     """ ++ Fragments.whereAndOpt(Some(idFilter))).update.run
    (for {
      oldUpload <- oldUploadIO
      newStatus <- upload.uploadStatus.pure[ConnectionIO]
      nAffected <- recordUpdateIO
      userPlatform <- UserDao.unsafeGetUserPlatform(user.id)
    } yield (oldUpload, newStatus, nAffected, userPlatform)) flatMap {
      case (
          oldUpload: GeojsonUpload,
          newStatus: UploadStatus,
          nAffected: Int,
          platform: Platform
          ) => {
        (
          oldUpload.uploadStatus,
          newStatus,
          platform.publicSettings.emailIngestNotification,
          user.getEmail
        ) match {
          case (_, _, _, "") | (_, _, false, _) => {
            logger.info(
              s"GeoJson upload complete, but user ${oldUpload.createdBy} or platform ${platform.name} has not requested email notifications"
            )
            nAffected.pure[ConnectionIO]
          }
          case (UploadStatus.Processing, UploadStatus.Failed, true, _) => {
            logger.info(
              s"notifying user ${user.id} that their geojson upload failed"
            )
            UploadNotifier(platform.id, id, MessageType.UploadFailed).send *>
              nAffected.pure[ConnectionIO]
          }
          case (UploadStatus.Processing, UploadStatus.Complete, true, _) => {
            logger.info(
              s"Notifying user ${user.id} that their geojson upload succeeded"
            )
            UploadNotifier(platform.id, id, MessageType.UploadSucceeded).send *>
              nAffected.pure[ConnectionIO]
          }
          case _ => {
            logger.debug(
              "No need to send notifications, status transition isn't something users care about"
            )
            nAffected.pure[ConnectionIO]
          }
        }
      }
    }
  }

  def getLayerUpload(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID,
      uploadId: UUID
  ): ConnectionIO[Option[GeojsonUpload]] =
    query
      .filter(fr"project_id = $projectId")
      .filter(fr"project_layer_id = $layerId")
      .filter(fr"annotation_group = $annotationGroupId")
      .filter(fr"id = $uploadId")
      .selectOption

  def listUploadsForAnnotationGroup(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID,
      page: PageRequest
  ): ConnectionIO[PaginatedResponse[GeojsonUpload]] =
    query
      .filter(fr"project_id = $projectId")
      .filter(fr"project_layer_id = $layerId")
      .filter(fr"annotation_group = $annotationGroupId")
      .page(page,
            Map(
              "createdAt" -> Order.Desc,
              "id" -> Order.Desc
            ))

  def deleteProjectLayerUpload(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID,
      uploadId: UUID
  ): ConnectionIO[Int] =
    query
      .filter(sql"project_id = $projectId")
      .filter(sql"project_layer_id = $layerId")
      .filter(fr"annotation_group = $annotationGroupId")
      .filter(uploadId)
      .delete
}
