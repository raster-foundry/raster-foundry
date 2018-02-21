package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{Scene, User}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID


object SceneDao extends Dao[Scene] {

  val tableName = "scenes"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, ingest_size_bytes, visibility, tags,
      datasource, scene_metadata, name, tile_footprint,
      data_footprint, metadata_files, ingest_location, cloud_cover,
      acquisition_date, sun_azimuth, sun_elevation, thumbnail_status,
      boundary_status, ingest_status
    FROM
  """ ++ tableF

  def insert(newScene: Scene.Create, user: User): ConnectionIO[Scene.WithRelated] = ???
}