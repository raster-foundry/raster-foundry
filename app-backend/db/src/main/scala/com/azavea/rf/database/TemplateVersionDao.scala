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

object TemplateVersionDao extends Dao[TemplateVersion] {
  val tableName = "template_versions"

  val selectF = sql"""
    SELECT
    FROM """ ++ tableF

  def insert(
    tvCreate: TemplateVersion.Create
  ): ConnectionIO[TemplateVersion] = {
    sql"""
      INSERT INTO template_versions
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

  def update(tv: TemplateVersion, id: UUID, user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp(new java.util.Date().getTime())
    (fr"""
      UPDATE template_versions
      SET
      modified_at = ${updateTime},
      modified_by = ${user.id},
      description = ${tv.description},
      changelog = ${tv.changelog}
      WHERE id = ${id} AND owner = ${user.id}
    """).update.run
  }

  def getById(templateO: Option[Template], templateVersionId: Long, user: User):
      ConnectionIO[Option[TemplateVersion.WithRelated]] = {
    templateO match {
      case Some(template) =>
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
      case None => Option.empty[TemplateVersion.WithRelated].pure[ConnectionIO]
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
    tv.id, tv.created_at, tv.modified_at, tv.created_by, tv.created_by, tv.version,
    tv.description, tv.changelog, tv.template_id, tv.analysis_id,
    a.id, a.created_at, a.created_by, a.modified_at, a.modified_by,
    a.visibility, a.organization_id, a.execution_parameters, a.owner, a.name, a.readonly
    FROM template_versions tv JOIN analyses a on tv.analysis_id = a.id
    WHERE tv.template_id = ${template.id}
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
