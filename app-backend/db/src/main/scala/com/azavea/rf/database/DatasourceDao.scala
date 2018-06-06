package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
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
        name, visibility, composites, extras, bands, license_name
      FROM
    """ ++ tableF

  def unsafeGetDatasourceById(datasourceId: UUID): ConnectionIO[Datasource] =
    query.filter(datasourceId).select

  def getDatasourceById(datasourceId: UUID): ConnectionIO[Option[Datasource]] =
    query.filter(datasourceId).selectOption

  def listDatasources(page: PageRequest, params: DatasourceQueryParameters, user: User): ConnectionIO[PaginatedResponse[Datasource]] = {
    DatasourceDao.query.filter(params)
      .page(page)
  }

  def create(
    datasource: Datasource,
    user: User
  ): ConnectionIO[Datasource] = {
    val ownerId = util.Ownership.checkOwner(user, Some(datasource.owner))
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, modified_by, owner,
      name, visibility, composites, extras, bands, license_name)
    VALUES
      (${datasource.id}, ${datasource.createdAt}, ${datasource.createdBy}, ${datasource.modifiedAt},
      ${datasource.modifiedBy}, ${ownerId}, ${datasource.name},
      ${datasource.visibility}, ${datasource.composites},
      ${datasource.extras}, ${datasource.bands}, ${datasource.licenseName})
    """).update.withUniqueGeneratedKeys[Datasource](
      "id", "created_at", "created_by", "modified_at", "modified_by", "owner",
      "name", "visibility", "composites", "extras", "bands", "license_name"
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
      where id = ${id}
      """
    updateQuery.update.run
  }

  def deleteDatasource(id: UUID, user: User): ConnectionIO[Int] = {
    (fr"DELETE FROM " ++ this.tableF ++ fr"WHERE id = ${id}")
      .update
      .run
  }

  def createDatasource(dsCreate: Datasource.Create, user: User): ConnectionIO[Datasource] = {
    val datasource = dsCreate.toDatasource(user)
    this.create(datasource, user)
  }
}
