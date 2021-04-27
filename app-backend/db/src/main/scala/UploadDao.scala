package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.notification._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object UploadDao extends Dao[Upload] {

  val tableName = "uploads"

  val selectF = sql"""
    SELECT
       id, created_at, created_by, modified_at,
       owner, upload_status, file_type, upload_type,
       files, datasource, metadata, visibility, project_id,
       layer_id, source, keep_in_source_bucket, bytes_uploaded,
       annotation_project_id, generate_tasks
    FROM
  """ ++ tableF

  def getUserBytesUploaded(
      user: User,
      excludeIdOpt: Option[UUID] = None
  ): ConnectionIO[Long] = {
    val excludeIdF = excludeIdOpt match {
      case Some(id) => fr"and id <> ${id}"
      case _        => fr""
    }
    (fr"SELECT COALESCE(SUM(bytes_uploaded), 0) FROM uploads WHERE owner = ${user.id}" ++ excludeIdF)
      .query[Long]
      .unique
  }

  def getUploadById(uploadId: UUID): ConnectionIO[Option[Upload]] =
    query.filter(uploadId).selectOption

  def unsafeGetUploadById(uploadId: UUID): ConnectionIO[Upload] =
    query.filter(uploadId).select

  def insert(
      newUpload: Upload.Create,
      user: User,
      bytesUploaded: Long
  ): ConnectionIO[Upload] =
    for {
      ownerPlatform <- newUpload.owner traverse { userId =>
        UserDao.unsafeGetUserPlatform(userId) map { _.id }
      }
      userPlatform <- UserDao.unsafeGetUserPlatform(user.id)
      userPlatformAdmin <- PlatformDao.userIsAdmin(user, userPlatform.id)
      // Use project defaultLayerId as layerId if projectId is provided
      // but layerId is not provided
      // Use posted Upload.Create without modifications in other cases
      projectO <- (newUpload.projectId, newUpload.layerId) match {
        case (Some(projectId), None) => ProjectDao.getProjectById(projectId)
        case _                       => None.pure[ConnectionIO]
      }
      updatedUpload = projectO match {
        case Some(project) =>
          newUpload.copy(layerId = Some(project.defaultLayerId))
        case _ => newUpload
      }
      upload = updatedUpload.toUpload(
        user,
        (userPlatform.id, userPlatformAdmin),
        ownerPlatform,
        bytesUploaded
      )
      insertedUpload <- (
          sql"""
       INSERT INTO uploads
         (id, created_at, created_by, modified_at,
          owner, upload_status, file_type, upload_type,
          files, datasource, metadata, visibility, project_id,
          layer_id, source, keep_in_source_bucket, bytes_uploaded, annotation_project_id,
          generate_tasks)
       VALUES (
         ${upload.id}, ${upload.createdAt}, ${upload.createdBy}, ${upload.modifiedAt},
         ${upload.owner}, ${upload.uploadStatus}, ${upload.fileType}, ${upload.uploadType},
         ${upload.files}, ${upload.datasource}, ${upload.metadata}, ${upload.visibility}, ${upload.projectId},
         ${upload.layerId}, ${upload.source}, ${upload.keepInSourceBucket}, ${upload.bytesUploaded},
         ${upload.annotationProjectId}, ${upload.generateTasks}
       )
      """.update.withUniqueGeneratedKeys[Upload](
            "id",
            "created_at",
            "created_by",
            "modified_at",
            "owner",
            "upload_status",
            "file_type",
            "upload_type",
            "files",
            "datasource",
            "metadata",
            "visibility",
            "project_id",
            "layer_id",
            "source",
            "keep_in_source_bucket",
            "bytes_uploaded",
            "annotation_project_id",
            "generate_tasks"
          )
      )
    } yield insertedUpload

  def update(upload: Upload, id: UUID): ConnectionIO[Int] = {
    val idFilter = fr"id = ${id}"
    val oldUploadIO = unsafeGetUploadById(id)
    val recordUpdateIO = (sql"""
       UPDATE uploads
       SET
          modified_at = NOW(),
          upload_status = ${upload.uploadStatus},
          file_type = ${upload.fileType},
          upload_type = ${upload.uploadType},
          files = ${upload.files},
          datasource = ${upload.datasource},
          metadata = ${upload.metadata},
          visibility = ${upload.visibility},
          project_id = ${upload.projectId},
          layer_id = ${upload.layerId},
          source = ${upload.source},
          keep_in_source_bucket = ${upload.keepInSourceBucket},
          bytes_uploaded = ${upload.bytesUploaded},
          annotation_project_id = ${upload.annotationProjectId},
          generate_tasks = ${upload.generateTasks}
     """ ++ Fragments.whereAndOpt(Some(idFilter))).update.run
    (for {
      oldUpload <- oldUploadIO
      newStatus = upload.uploadStatus
      nAffected <- recordUpdateIO
      userPlatform <- UserDao.unsafeGetUserPlatform(oldUpload.owner)
      owner <- UserDao.unsafeGetUserById(oldUpload.owner)
    } yield (oldUpload, newStatus, nAffected, userPlatform, owner)) flatMap {
      case (
            oldUpload: Upload,
            newStatus: UploadStatus,
            nAffected: Int,
            platform: Platform,
            owner: User
          ) => {
        (
          oldUpload.uploadStatus,
          newStatus,
          platform.publicSettings.emailIngestNotification,
          owner.getEmail
        ) match {
          case (_, _, _, "") | (_, _, false, _) => {
            logger.info(
              s"Upload complete, but user ${owner.id} or platform ${platform.name} has not requested email notifications"
            )
            nAffected.pure[ConnectionIO]
          }
          case (UploadStatus.Processing, UploadStatus.Failed, true, _) => {
            logger.info(s"notifying user ${owner.id} that their upload failed")
            UploadNotifier(platform.id, id, MessageType.UploadFailed).send *>
              nAffected.pure[ConnectionIO]
          }
          case (UploadStatus.Processing, UploadStatus.Complete, true, _) => {
            logger.info(
              s"Notifying user ${owner.id} that their upload succeeded"
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

  def findForAnnotationProject(
      annotationProjectId: UUID
  ): ConnectionIO[List[Upload]] =
    query.filter(fr"annotation_project_id = $annotationProjectId").list
}
