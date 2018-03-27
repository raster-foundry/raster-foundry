package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

object WorkspaceDao extends Dao[Workspace] {
  val tableName = "workspaces"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, created_by, modified_by, owner, organization_id, name,
      description, active_analysis
    FROM """ ++ tableF

  def insert(newWorkspace: Workspace.Create, user: User): ConnectionIO[Workspace] = {
    val id = UUID.randomUUID()
    val now = new Timestamp(new java.util.Date().getTime())
    val ownerId = util.Ownership.checkOwner(user, newWorkspace.owner)

    sql"""
      INSERT INTO workspaces
      (
      id, created_at, modified_at, created_by, modified_by, owner,
      organization_id, name, description
      )
      VALUES
      (
      ${id}, ${now}, ${now}, ${user.id}, ${user.id}, ${ownerId}, ${newWorkspace.organizationId},
      ${newWorkspace.name}, ${newWorkspace.description}
      )
    """.update.withUniqueGeneratedKeys[Workspace](
      "id", "created_at", "modified_at", "created_by", "modified_by", "owner",
      "organization_id", "name", "description", "active_analysis"
    )
  }

  def getById(workspaceId: UUID, user: User): ConnectionIO[Option[Workspace]] =
    query
      .filter(fr"id = ${workspaceId}")
      .ownerFilter(user)
      .selectOption

  def update(update: Workspace.Update, id: UUID, user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp(new java.util.Date().getTime())
    val idFilter = fr"id = ${id}"
    (sql"""
      UPDATE ${tableName}
      SET
        modified_by = ${user.id},
        modified_at = ${updateTime},
        name = ${update.name},
        description = ${update.description},
        active_analysis = ${update.activeAnalysis}
     """ ++ Fragments.whereAndOpt(ownerEditFilter(user), Some(idFilter))).update.run
  }

  def addAnalysis(workspaceId: UUID, analysisCreate: Analysis.Create, user: User): ConnectionIO[Option[Analysis]] = {
    WorkspaceDao.query
      .filter(fr"id = ${workspaceId}")
      .ownerFilter(user)
      .selectOption
      .flatMap {
        case Some(workspace) =>
          AnalysisDao.insertAnalysis(analysisCreate, user).map(Some(_))
        case None =>
          Option.empty[Analysis].pure[ConnectionIO]
      }
  }

  def deleteAnalysis(workspaceId: UUID, analysisId: UUID, user: User): ConnectionIO[Int] =
    (
      getById(workspaceId, user),
      AnalysisDao.getById(analysisId, user)
    ).tupled.flatMap {
        case (Some(workspace), Some(analysis)) =>
          WorkspaceAnalysisDao.delete(workspace, analysis)
        case _ =>
          0.pure[ConnectionIO]
      }

  def getAnalyses(workspaceId: UUID, user: User): ConnectionIO[Option[List[Analysis]]] = {
    getById(workspaceId, user).flatMap {
      case Some(workspace) =>
        WorkspaceAnalysisDao.getWorkspaceAnalyses(workspace, user).map(Some(_))
      case None => Option.empty[List[Analysis]].pure[ConnectionIO]
    }
  }
}
