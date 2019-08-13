package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.PageRequest

import java.sql.Timestamp
import java.util.{UUID, Date}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

object StacExportDao extends Dao[StacExport] {
  val tableName = "stac_exports"

  val selectF: Fragment = sql"""
      SELECT
        id, created_at, created_by, modified_at, modified_by, owner,
        name, export_location, export_status, layer_definitions, union_aois,
        task_statuses
      FROM
    """ ++ tableF

  def unsafeGetById(id: UUID): ConnectionIO[StacExport] =
    query.filter(id).select

  def getById(id: UUID): ConnectionIO[Option[StacExport]] =
    query.filter(id).selectOption

  def list(page: PageRequest,
           params: StacExportQueryParameters,
           user: User): ConnectionIO[PaginatedResponse[StacExport]] =
    query
      .filter(params)
      .filter(user.isSuperuser && user.isActive match {
        case true  => fr""
        case false => fr"owner = ${user.id}"
      })
      .page(page)

  def create(newStacExport: StacExport.Create,
             user: User): ConnectionIO[StacExport] = {
    val newExport = newStacExport.toStacExport(user)
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, modified_by, owner,
      name, export_location, export_status, layer_definitions, union_aois,
      task_statuses)
    VALUES
      (${newExport.id}, ${newExport.createdAt}, ${newExport.createdBy}, ${newExport.modifiedAt},
      ${newExport.modifiedBy}, ${newExport.owner}, ${newExport.name}, ${newExport.exportLocation},
      ${newExport.exportStatus}, ${newExport.layerDefinitions}, ${newExport.unionAois},
      ${newExport.taskStatuses})
    """).update.withUniqueGeneratedKeys[StacExport](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "owner",
      "name",
      "export_location",
      "export_status",
      "layer_definitions",
      "union_aois",
      "task_statuses"
    )
  }

  def update(stacExport: StacExport,
             id: UUID,
             user: User): ConnectionIO[Int] = {
    val now = new Timestamp(new Date().getTime)
    (fr"UPDATE" ++ this.tableF ++ fr"SET" ++ fr"""
      modified_at = ${now},
      modified_by = ${user.id},
      name = ${stacExport.name},
      export_location = ${stacExport.exportLocation},
      export_status = ${stacExport.exportStatus}
      where id = ${id}
      """).update.run
  }

  def delete(id: UUID): ConnectionIO[Int] = {
    (fr"DELETE FROM " ++ this.tableF ++ fr"WHERE id = ${id}").update.run
  }

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

  def hasProjectViewAccess(layerDefinitions: List[StacExport.LayerDefinition],
                           user: User): ConnectionIO[Boolean] =
    layerDefinitions traverse { ld =>
      ProjectDao.authProjectLayerExist(ld.projectId,
                                       ld.layerId,
                                       user,
                                       ActionType.View)
    } map { _.foldLeft(true)(_ && _) }
}
