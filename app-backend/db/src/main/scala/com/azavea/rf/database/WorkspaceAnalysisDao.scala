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

object WorkspaceAnalysisDao extends Dao[WorkspaceAnalysis] {
  val tableName = "workspace_analyses"

  val selectF = sql"""
  SELECT * FROM
  """ ++ tableF

  def insert(workspace: Workspace, analysis: Analysis): ConnectionIO[WorkspaceAnalysis] = {
    sql"""
       INSERT INTO workspace_analyses
         (workspace_id, analysis_id)
       VALUES
          (${workspace.id}, ${analysis.id})
       """.update.withUniqueGeneratedKeys[WorkspaceAnalysis](
      "workspace_id", "analysis_id"
    )
  }

  def delete(workspace: Workspace, analysis: Analysis): ConnectionIO[Int] = {
    query.filter(fr"workspace_id = ${workspace.id}")
    .filter(fr"analysis_id = ${analysis.id}")
    .delete
  }

  def getWorkspaceAnalyses(workspace: Workspace, user: User): ConnectionIO[List[Analysis]] = {
    query.filter(fr"workspace_id = ${workspace.id}")
      .list
      .flatMap(
        workspaceAnalyses =>
        AnalysisDao.query.filter(
            workspaceAnalyses
              .map(_.analysisId)
              .toNel
              .map(ids =>
                Fragments.in(fr"id", ids)
              )
        ).list
      )
  }
}
