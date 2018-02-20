package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.Upload

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

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

}

