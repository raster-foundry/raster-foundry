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

import scala.concurrent.Future


object AnalysisDao extends Dao[Analysis] {

  val tableName = "analyses"

  val selectF = sql"""
    SELECT
      id, name, created_at, created_by, modified_at, modified_by, owner, visibility,
      organization_id, execution_parameters, readonly
    FROM
  """ ++ tableF

  def insertAnalysis(newRun: Analysis.Create, user: User): ConnectionIO[Analysis] = {
    val now = new Timestamp(new java.util.Date().getTime())
    val id = UUID.randomUUID()

    sql"""
          INSERT INTO analyses
            (id, name, created_at, created_by, modified_at, modified_by, owner, visibility, organization_id,
             execution_parameters, readonly)
          VALUES
            (${id}, ${newRun.name}, ${now}, ${user.id}, ${now}, ${user.id}, ${newRun.owner.getOrElse(user.id)},
             ${newRun.visibility}, ${newRun.organizationId}, ${newRun.executionParameters}, ${newRun.readonly.getOrElse(false)})
       """.update.withUniqueGeneratedKeys[Analysis](
      "id", "name", "created_at", "created_by", "modified_at", "modified_by", "owner", "visibility",
      "organization_id", "execution_parameters", "readonly"
    )
  }

  def updateAnalysis(analysisUpdate: Analysis, id: UUID, user: User): ConnectionIO[Int] = {
    val now = new Timestamp(new java.util.Date().getTime())
    val idFilter = fr"id = ${id}"

    (sql"""
       UPDATE analyses
       SET
         name = ${analysisUpdate.name},
         modified_at = ${now},
         modified_by = ${user.id},
         visibility = ${analysisUpdate.visibility},
         execution_parameters = ${analysisUpdate.executionParameters},
         readonly = ${analysisUpdate.readonly}
       """ ++ Fragments.whereAndOpt(ownerEditFilter(user), Some(idFilter), Some(fr"readonly = false"))).update.run
  }

  def getById(analysisId: UUID, user: User): ConnectionIO[Option[Analysis]] = {
    query.filter(analysisId).ownerFilter(user).selectOption
  }
}

