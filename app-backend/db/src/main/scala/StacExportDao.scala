package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.PageRequest
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._

import java.sql.Timestamp
import java.util.{Date, UUID}

object StacExportDao extends Dao[StacExport] {
  val tableName = "stac_exports"

  val selectF: Fragment = sql"""
      SELECT
        id, created_at, created_by, modified_at, owner,
        name, license, export_location, export_status,
        task_statuses, annotation_project_id, campaign_id
      FROM
    """ ++ tableF

  def unsafeGetById(id: UUID): ConnectionIO[StacExport] =
    query.filter(id).select

  def getById(id: UUID): ConnectionIO[Option[StacExport]] =
    query.filter(id).selectOption

  def list(
      page: PageRequest,
      params: StacExportQueryParameters,
      user: User
  ): ConnectionIO[PaginatedResponse[StacExport]] =
    query
      .filter(params)
      .filter(user.isSuperuser && user.isActive match {
        case true  => fr""
        case false => fr"owner = ${user.id}"
      })
      .page(page)

  def create(
      newStacExport: StacExport.Create,
      user: User
  ): ConnectionIO[StacExport] = {
    val newExport = newStacExport.toStacExport(user)
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, owner,
      name, license, export_location, export_status,
      task_statuses, annotation_project_id, campaign_id)
    VALUES
      (${newExport.id}, ${newExport.createdAt}, ${newExport.createdBy}, ${newExport.modifiedAt},
      ${newExport.owner}, ${newExport.name}, ${newExport.license}, ${newExport.exportLocation},
      ${newExport.exportStatus}, ${newExport.taskStatuses}, ${newExport.annotationProjectId},
      ${newExport.campaignId})
    """).update.withUniqueGeneratedKeys[StacExport](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "owner",
      "name",
      "license",
      "export_location",
      "export_status",
      "task_statuses",
      "annotation_project_id",
      "campaign_id"
    )
  }

  def update(stacExport: StacExport, id: UUID): ConnectionIO[Int] = {
    val now = new Timestamp(new Date().getTime)
    (fr"UPDATE" ++ this.tableF ++ fr"SET" ++ fr"""
      modified_at = ${now},
      name = ${stacExport.name},
      export_location = ${stacExport.exportLocation},
      export_status = ${stacExport.exportStatus}
      where id = ${id}
      """).update.run
  }

  def delete(id: UUID): ConnectionIO[Int] =
    query.filter(id).delete

  def isOwnerOrSuperUser(user: User, id: UUID): ConnectionIO[Boolean] =
    for {
      exportO <- getById(id)
      isSuperuser = user.isSuperuser && user.isActive
    } yield {
      exportO match {
        case Some(export) => export.owner == user.id || isSuperuser
        case _            => isSuperuser
      }
    }

  def hasProjectViewAccess(
      annotationProjectId: UUID,
      user: User
  ): ConnectionIO[Boolean] =
    for {
      authProject <- AnnotationProjectDao.authorized(
        user,
        ObjectType.AnnotationProject,
        annotationProjectId,
        ActionType.View
      )
    } yield authProject.toBoolean

}
