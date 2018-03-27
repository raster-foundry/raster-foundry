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

import com.azavea.rf.datamodel.Template

object TemplateDao extends Dao[Template] {
  val tableName = "templates"

  val selectF = fr"""
    SELECT
      id, created_at, modified_at, created_by, modified_by, owner, organization_id, name,
      description, details, requirements, license, visibility, compatible_data_sources
    FROM """ ++ tableF

  def insert(newTemplate: Template.Create, user: User): ConnectionIO[Template] = {
    val id = UUID.randomUUID()
    val now = new Timestamp(new java.util.Date().getTime())
    val ownerId = util.Ownership.checkOwner(user, newTemplate.owner)
    val license = newTemplate.license.getOrElse("none");
    fr"""
       INSERT INTO templates
         (id, created_at, modified_at, created_by, modified_by, owner, organization_id, name,
          description, details, requirements, license, visibility, compatible_data_sources)
       VALUES
         (${id}, ${now}, ${now}, ${user.id}, ${user.id}, ${ownerId}, ${newTemplate.organizationId}, ${newTemplate.name},
          ${newTemplate.description}, ${newTemplate.details}, ${newTemplate.requirements}, ${license}, ${newTemplate.visibility},
          ${newTemplate.compatibleDataSources})
       """.update.withUniqueGeneratedKeys[Template](
      "id", "created_at", "modified_at", "created_by", "modified_by", "owner", "organization_id", "name",
      "description", "details", "requirements", "license", "visibility", "compatible_data_sources"
    )
  }

  def update(template: Template, id: UUID, user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp(new java.util.Date().getTime())
    val idFilter = fr"id = ${id}"
    (fr"""
       UPDATE templates
       SET
         modified_by = ${user.id},
         modified_at = ${updateTime},
         name = ${template.name},
         description = ${template.description},
         details = ${template.details},
         requirements = ${template.requirements},
         license = ${template.license},
         visibility = ${template.visibility},
         compatible_data_sources = ${template.compatibleDataSources}
     """ ++ Fragments.whereAndOpt(ownerEditFilter(user), Some(idFilter))).update.run
  }

  def getById(templateId: UUID, user: User): ConnectionIO[Option[Template]] = {
    query.filter(templateId).ownerFilter(user).selectOption
  }

  def publish(templateId: UUID, user: User, tv: TemplateVersion.CreateWithRelated): ConnectionIO[Option[TemplateVersion.WithRelated]] = {
    getById(templateId, user) flatMap {
      case Some(template) =>
        AnalysisDao.insertAnalysis(tv.analysis, user) flatMap { analysis =>
          TemplateVersionDao.createFromCreateRelated(template, analysis, tv, user).map(Some(_))
        }
      case _ => Option.empty[TemplateVersion.WithRelated].pure[ConnectionIO]
    }
  }
}
