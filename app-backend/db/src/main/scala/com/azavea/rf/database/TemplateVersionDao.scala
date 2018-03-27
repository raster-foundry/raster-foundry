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

object TemplateVersionDao extends Dao[TemplateVersion] {
  val tableName = "template_versions"

  val selectF = sql"""
    SELECT
    FROM """ ++ tableF

  def insert(
    tvCreate: TemplateVersion.Create
  ): ConnectionIO[TemplateVersion] = {
    sql"""
      INSERT INTO ${tableName}
      (created_at, modified_at, created_by, modified_by, version, description, changelog, template_id, analysis_id)
      VALUES
      (${tvCreate.createdAt}, ${tvCreate.modifiedAt}, ${tvCreate.createdBy}, ${tvCreate.modifiedBy},
      ${tvCreate.version}, ${tvCreate.description}, ${tvCreate.changelog}, ${tvCreate.templateId},
      ${tvCreate.analysisId})
    """.update.withUniqueGeneratedKeys[TemplateVersion](
      "id", "created_at", "modified_at", "created_by", "modified_by", "version", "description", "changelog",
      "template_id", "analysis_id"
    )
  }

  def getById(template: Template, templateVersionId: Long, user: User):
      ConnectionIO[Option[TemplateVersion.WithRelated]] = {
    query
      .filter(fr"template_id = ${template.id}")
      .selectOption.flatMap {
        case Some(tv) =>
          AnalysisDao.getById(tv.analysisId, user) map {
            case Some(analysis) =>
              Some(tv.withRelatedFromComponents(analysis))
            case _ =>
              None
          }
        case _ => Option.empty[TemplateVersion.WithRelated].pure[ConnectionIO]
    }
  }

  def createFromCreateRelated(
    template: Template, analysis: Analysis,
    createWithRelated: TemplateVersion.CreateWithRelated, user: User
  ): ConnectionIO[TemplateVersion.WithRelated] = {
    insert(createWithRelated.toCreate(user, analysis))
      .map(templateVersion => templateVersion.withRelatedFromComponents(analysis))
  }

  def getLatestVersion(template: Template, user: User): ConnectionIO[Option[TemplateVersion.WithRelated]] = {
    val latestQuery = sql"""
    SELECT
    tv.*, analyses.*
    FROM template_versions tv JOIN analyses on template_versions.analysis_id = analyses.id
    WHERE tv.id = ${template.id}
    ORDER BY tv.id DESC
    LIMIT 1
    """.query[(TemplateVersion, Analysis)].option
    latestQuery map {
      case Some((templateVersion, analysis)) =>
        Some(templateVersion.withRelatedFromComponents(analysis))
      case None => None
    }
  }
}
