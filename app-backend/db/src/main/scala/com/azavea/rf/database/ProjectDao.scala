package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import geotrellis.slick.Projected
import geotrellis.vector.Polygon

import java.util.UUID
import java.sql.Timestamp


object ProjectDao extends Dao[Project] {

  val tableName = "projects"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, organization_id, created_by,
      modified_by, owner, name, slug_label, description,
      visibility, tile_visibility, is_aoi_project,
      aoi_cadence_millis, aois_last_checked, tags, extent,
      manual_order, is_single_band, single_band_options
    FROM
  """ ++ tableF

  def select(id: UUID) =
    (selectF ++ fr"WHERE id = $id").query[Project].unique

  def create(
    user: User,
    owner: Option[String],
    organizationId: UUID,
    name: String,
    description: String,
    visibility: Visibility,
    tileVisibility: Visibility,
    isAOIProject: Boolean = false,
    aoiCadenceMillis: Long = Project.DEFAULT_CADENCE,
    tags: List[String],
    isSingleBand: Boolean = false,
    singleBandOptions: Option[SingleBandOptions.Params]
  ): ConnectionIO[Project] = {
    val id = UUID.randomUUID()
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, owner)
    val slug = Project.slugify(name)
    val userId = user.id
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, organization_id, created_by,
        modified_by, owner, name, slug_label, description,
        visibility, tile_visibility, is_aoi_project,
        aoi_cadence_millis, aois_last_checked, tags, extent,
        manual_order, is_single_band, single_band_options)
      VALUES
        ($id, $now, $now, $organizationId, $userId,
        $userId, $ownerId, $name, $slug, $description,
        $visibility, $tileVisibility, $isAOIProject,
        $aoiCadenceMillis, $aoiCadenceMillis, $tags, null,
        TRUE, $isSingleBand, $singleBandOptions)
    """).update.withUniqueGeneratedKeys[Project](
      "id", "created_at", "modified_at", "organization_id", "created_by",
      "modified_by", "owner", "name", "slug_label", "description",
      "visibility", "tile_visibility", "is_aoi_project",
      "aoi_cadence_millis", "aois_last_checked", "tags", "extent",
      "manual_order", "is_single_band", "single_band_options"
    )
  }
}

