package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
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

  def select(id: UUID) =
    (selectF ++ fr"WHERE id = $id").query[Datasource].unique

  def listFilters(params: DatasourceQueryParameters, user: User): List[Option[Fragment]] =
    List(params.name.map({ name => fr"name = $name" }))

  def create(
    user: User,
    organizationId: UUID,
    name: String,
    visibility: Visibility,
    owner: Option[String],
    composites: Json,
    extras: Json,
    bands: Json
  ): ConnectionIO[Datasource] = {
    val id = UUID.randomUUID
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, owner)
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, name, visibility, composites, extras, bands)
    VALUES
      ($id, $now, ${user.id}, $now, ${user.id}, $ownerId,
      $organizationId, $name, $visibility, $composites, $extras, $bands)
    """).update.withUniqueGeneratedKeys[Datasource](
      "id", "created_at", "created_by", "modified_at", "modified_by", "owner",
      "organization_id", "name", "visibility", "composites", "extras", "bands"
    )
  }
}

