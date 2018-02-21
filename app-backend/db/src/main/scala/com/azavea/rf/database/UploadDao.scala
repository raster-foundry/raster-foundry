package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{Upload, User}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID


object UploadDao extends Dao[Upload] {

  val tableName = "uploads"

  val selectF = sql"""
    SELECT
       id, created_at, created_by, modified_at, modified_by,
       organization_id, upload_status, file_type, upload_type,
       files, datasource, metadata, visibility, owner, project_id,
       source
    FROM
  """ ++ tableF

  def insert(newUpload: Upload.Create, user: User): ConnectionIO[Upload] = {
    val upload = newUpload.toUpload(user)
    sql"""
       INSERT INTO uploads
         (id, created_at, created_by, modified_at, modified_by,
          organization_id, upload_status, file_type, upload_type,
          files, datasource, metadata, visibility, owner, project_id,
          source)
       VALUES (
         ${upload.id}, ${upload.createdAt}, ${upload.createdBy}, ${upload.modifiedAt}, ${upload.modifiedBy},
         ${upload.organizationId}, ${upload.uploadStatus}, ${upload.fileType}, ${upload.uploadType}, ${upload.files},
         ${upload.datasource}, ${upload.metadata}, ${upload.visibility}, ${upload.owner}, ${upload.projectId},
         ${upload.source}
       )
      """.update.withUniqueGeneratedKeys[Upload](
      "id", "created_at", "created_by", "modified_at", "modified_by",
      "organization_id", "upload_status", "file_type", "upload_type",
      "files", "datasource", "metadata", "visibility", "owner", "project_id",
      "source"
    )
  }

  def update(upload: Upload, id: UUID, user: User): ConnectionIO[Int] = {
    val idFilter = fr"id = ${id}"
    (sql"""
       UPDATE uploads
       SET
          modified_at = NOW(),
          modified_by = ${user.id},
          upload_status = ${upload.uploadStatus},
          file_type = ${upload.fileType},
          upload_type = ${upload.uploadType},
          files = ${upload.files},
          datasource = ${upload.datasource},
          metadata = ${upload.metadata},
          visibility = ${upload.visibility},
          project_id = ${upload.projectId},
          source = ${upload.source}
     """ ++ Fragments.whereAndOpt(ownerEditFilter(user), Some(idFilter))).update.run
  }
}

