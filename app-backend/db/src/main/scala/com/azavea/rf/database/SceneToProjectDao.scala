package com.azavea.rf.database

import java.util.UUID

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{BatchParams, ColorCorrect, SceneToProject}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._

import scala.concurrent.Future


object SceneToProjectDao extends Dao[SceneToProject] {

  val tableName = "scenes_to_projects"

  val selectF = sql"""
    SELECT
      scene_id, project_id, scene_order, mosaic_definition, accepted
    FROM
  """ ++ tableF

  /** Unclear what this is supposed to return from the current implementation */
  def acceptScene(projectId: UUID, sceneId: UUID) = ???

  def bulkAddScenes(projectId: UUID, scenesIds: Seq[UUID]): Future[Seq[UUID]] = ???

  def setManualOrder(projectId: UUID, sceneIds: Seq[UUID]): Future[Seq[UUID]] = ???

  // Check swagger spec for appropriate return type
  def getMosaicDefinition(projectId: UUID) = ???

  // I don't think return type here is important, should makybe be int since no content is returned
  def setColorCorrectParams(projectId: UUID, sceneId: UUID, colorCorrectParams: ColorCorrect.Params): Future[Seq[SceneToProject]] = ???

  // I don't think return type here is important, should makybe be int since no content is returned
  def setColorCorrectParamsBatch(projectId: UUID, batchParams: BatchParams): Future[Seq[SceneToProject]] = ???
}

