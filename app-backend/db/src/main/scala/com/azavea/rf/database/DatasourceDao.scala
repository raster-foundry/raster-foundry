package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.sql.Timestamp
import java.util.{Date, UUID}


object DatasourceDao extends Dao[Datasource] {

  val tableName = "datasources"

  val selectF = sql"""
      SELECT
        id, created_at, created_by, modified_at, modified_by, owner,
        organization_id, name, visibility, composites, extras, bands, license_name
      FROM
    """ ++ tableF

  def create(
    datasource: Datasource,
    user: User
  ): ConnectionIO[Datasource] = {
    val ownerId = util.Ownership.checkOwner(user, Some(datasource.owner))
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, name, visibility, composites, extras, bands, licenseName)
    VALUES
      (${datasource.id}, ${datasource.createdAt}, ${datasource.createdBy}, ${datasource.modifiedAt},
      ${datasource.modifiedBy}, ${ownerId}, ${datasource.organizationId}, ${datasource.name},
      ${datasource.visibility}, ${datasource.composites},
      ${datasource.extras}, ${datasource.bands}, ${datasource.licenseName})
    """).update.withUniqueGeneratedKeys[Datasource](
      "id", "created_at", "created_by", "modified_at", "modified_by", "owner",
      "organization_id", "name", "visibility", "composites", "extras", "bands", "license_name"
    )
  }

  def updateDatasource(datasource: Datasource, id: UUID, user: User): ConnectionIO[Int] = {
    // fetch datasource so we can check if user is allowed to update (access control)
    val now = new Timestamp((new java.util.Date()).getTime())
    val updateQuery =
      fr"UPDATE" ++ this.tableF ++ fr"SET" ++
      fr"""
      modified_at = ${now},
      modified_by = ${user.id},
      name = ${datasource.name},
      visibility = ${datasource.visibility},
      composites = ${datasource.composites},
      extras = ${datasource.extras},
      bands = ${datasource.bands},
      license_name = ${datasource.licenseName}
      where id = ${id} AND owner = ${user.id}
      """
    updateQuery.update.run
  }

  def deleteDatasource(id: UUID, user: User): ConnectionIO[Int] = {
    (fr"DELETE FROM " ++ this.tableF ++ fr" WHERE owner = ${user.id} AND id = ${id}")
      .update
      .run
  }

  def createDatasource(dsCreate: Datasource.Create, user: User): ConnectionIO[Datasource] = {
    val datasource = dsCreate.toDatasource(user)
    this.create(datasource, user)
  }
}

