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
        organization_id, name, visibility, composites, extras, bands
      FROM
    """ ++ tableF

  def create(
    datasource: Datasource,
    user: User
  ): ConnectionIO[Datasource] = {
    val ownerId = util.Ownership.checkOwner(user, Some(datasource.owner))
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, name, visibility, composites, extras, bands)
    VALUES
      (${datasource.id}, ${datasource.createdAt}, ${datasource.createdBy}, ${datasource.modifiedAt},
      ${datasource.modifiedBy}, ${ownerId}, ${datasource.organizationId}, ${datasource.name},
      ${datasource.visibility}, ${datasource.composites},
      ${datasource.extras}, ${datasource.bands})
    """).update.withUniqueGeneratedKeys[Datasource](
      "id", "created_at", "created_by", "modified_at", "modified_by", "owner",
      "organization_id", "name", "visibility", "composites", "extras", "bands"
    )
  }

  def updateDatasource(datasource: Datasource, id: UUID, user: User)
                      (implicit xa: Transactor[IO]): Future[Int] = {
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
      bands = ${datasource.bands}
      where id = $id
      """

    for {
      scenes <- {
        (fr"SELECT count(*) from" ++ this.tableF ++ fr"WHERE id = $id AND owner = ${user.id}")
          .query[Int].unique.transact(xa).unsafeToFuture
      }
      updateApplied <- updateQuery.update.run.transact(xa).unsafeToFuture if scenes == 1
    } yield updateApplied
  }

  def deleteDatasource(id: UUID, user: User)
                      (implicit xa: Transactor[IO]): Future[Boolean] = {
    (fr"DELETE FROM " ++ this.tableF ++ fr" WHERE owner = ${user.id} AND id = $id")
      .update
      .run
      .transact(xa)
      .unsafeToFuture.map((count: Int) => count > 0)
  }

  def getDatasource(id: UUID, user: User)
                   (implicit xa: Transactor[IO]): Future[Option[Datasource]] = {
    this.query
      .filter(fr"owner = ${user.id} or owner = 'default'")
      .selectOption
  }

  def createDatasource(dsCreate: Datasource.Create, user: User)
                      (implicit xa: Transactor[IO]): Future[Datasource] = {
    val datasource = dsCreate.toDatasource(user)
    this.create(datasource, user).transact(xa).unsafeToFuture().map(_ => datasource)
  }
}

