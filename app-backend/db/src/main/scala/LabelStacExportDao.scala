package com.rasterfoundry.database

import java.sql.Timestamp
import java.util.{UUID, Date}

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.PageRequest
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

object LabelStacExportDao extends Dao[LabelStacExport] {
  val tableName = "label_stac_exports"

  val selectF: Fragment = sql"""
      SELECT
        id, created_at, created_by, modified_at, modified_by, owner,
        name, export_location, export_status, layer_definition, is_union,
        task_statuses
      FROM
    """ ++ tableF

  def unsafeGetLabelStacExportById(id: UUID): ConnectionIO[LabelStacExport] =
    query.filter(id).select

  def getLabelStacExportById(id: UUID): ConnectionIO[Option[LabelStacExport]] =
    query.filter(id).selectOption

  def list(page: PageRequest, params: LabelStacExportQueryParameters)
    : ConnectionIO[PaginatedResponse[LabelStacExport]] =
    query.filter(params).page(page)

  def create(newLabelStacExport: LabelStacExport.Create,
             user: User): ConnectionIO[LabelStacExport] = {
    val newExport = newLabelStacExport.toLabelStacExport(user)
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, modified_by, owner,
      name, export_location, export_status, layer_definition, is_union,
      task_statuses)
    VALUES
      (${newExport.id}, ${newExport.createdAt}, ${newExport.createdBy}, ${newExport.modifiedAt},
      ${newExport.modifiedBy}, ${newExport.owner}, ${newExport.name}, ${newExport.exportLocation},
      ${newExport.exportStatus}, ${newExport.layerDefinition}, ${newExport.isUnion},
      ${newExport.taskStatuses})
    """).update.withUniqueGeneratedKeys[LabelStacExport](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "owner",
      "name",
      "export_location",
      "export_status",
      "layer_definition",
      "is_union",
      "task_statuses"
    )
  }

  def update(labelStacExport: LabelStacExport,
             id: UUID,
             user: User): ConnectionIO[Int] = {
    val now = new Timestamp(new Date().getTime)
    (fr"UPDATE" ++ this.tableF ++ fr"SET" ++ fr"""
      modified_at = ${now},
      modified_by = ${user.id},
      name = ${labelStacExport.name},
      export_location = ${labelStacExport.exportLocation},
      export_status = ${labelStacExport.exportStatus}
      where id = ${id}
      """).update.run
  }

  def delete(id: UUID): ConnectionIO[Int] = {
    (fr"DELETE FROM " ++ this.tableF ++ fr"WHERE id = ${id}").update.run
  }
}
