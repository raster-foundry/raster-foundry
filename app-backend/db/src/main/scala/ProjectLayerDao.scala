package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.Cache
import com.rasterfoundry.datamodel.PageRequest
import com.rasterfoundry.datamodel._

import cats.effect.LiftIO
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._
import scalacache.CatsEffect.modes._
import scalacache._

import scala.concurrent.duration._

import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.UUID

object ProjectLayerDao extends Dao[ProjectLayer] {
  val tableName = "project_layers"

  import Cache.ProjectLayerCache._

  def deleteCache(id: UUID): ConnectionIO[Unit] = {
    for {
      _ <- remove(ProjectLayer.cacheKey(id))(
        projectLayerCache,
        async[ConnectionIO]
      ).attempt
    } yield ()
  }

  val selectAllColsF: Fragment = fr"""
    SELECT
      id, created_at, modified_at, name, project_id, color_group_hex,
      smart_layer_id, range_start, range_end, geometry, is_single_band,
      single_band_options, overviews_location, min_zoom_level
    """

  val selectF: Fragment =
    selectAllColsF ++ fr"from" ++ tableF

  def getProjectLayerById(
      projectLayerId: UUID
  ): ConnectionIO[Option[ProjectLayer]] =
    Cache.getOptionCache(
      ProjectLayer.cacheKey(projectLayerId),
      Some(30 minutes)
    ) {
      query.filter(projectLayerId).selectOption
    }

  def unsafeGetProjectLayerById(
      projectLayerId: UUID
  ): ConnectionIO[ProjectLayer] =
    cachingF(ProjectLayer.cacheKey(projectLayerId))(Some(30 minutes)) {
      query.filter(projectLayerId).select
    }

  def listProjectLayersForProjectQ(projectId: UUID) =
    query.filter(fr"project_id = ${projectId}")

  def listProjectLayersForProject(
      page: PageRequest,
      projectId: UUID
  ): ConnectionIO[PaginatedResponse[ProjectLayer]] =
    listProjectLayersForProjectQ(projectId).page(page)

  def listProjectLayersWithImagery(
      projectId: UUID
  ): ConnectionIO[List[ProjectLayer]] = {
    val tableF =
      fr"project_layers left join scenes_to_layers on project_layers.id = scenes_to_layers.project_layer_id"
    val queryBuilder = Dao.QueryBuilder[ProjectLayer](
      selectAllColsF ++ fr"from" ++ tableF,
      tableF,
      Nil
    )
    queryBuilder
      .filter(fr"scenes_to_layers.scene_id IS NOT NULL")
      .filter(fr"project_id = ${projectId}")
      .list
  }

  def insertProjectLayer(
      pl: ProjectLayer
  ): ConnectionIO[ProjectLayer] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
    (id, created_at, modified_at, name, project_id, color_group_hex,
    smart_layer_id, range_start, range_end, geometry, is_single_band, single_band_options,
    overviews_location, min_zoom_level
    )
    VALUES
      (${pl.id}, ${pl.createdAt}, ${pl.modifiedAt}, ${pl.name}, ${pl.projectId},
      ${pl.colorGroupHex}, ${pl.smartLayerId}, ${pl.rangeStart}, ${pl.rangeEnd},
      ${pl.geometry}, ${pl.isSingleBand}, ${pl.singleBandOptions}, ${pl.overviewsLocation},
      ${pl.minZoomLevel})
    """).update.withUniqueGeneratedKeys[ProjectLayer](
      "id",
      "created_at",
      "modified_at",
      "name",
      "project_id",
      "color_group_hex",
      "smart_layer_id",
      "range_start",
      "range_end",
      "geometry",
      "is_single_band",
      "single_band_options",
      "overviews_location",
      "min_zoom_level"
    )
  }

  def updateProjectLayerQ(projectLayer: ProjectLayer, id: UUID): Update0 = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${id}"
    val query = (fr"UPDATE" ++ tableF ++ fr"""SET
      modified_at = ${updateTime},
      name = ${projectLayer.name},
      color_group_hex = ${projectLayer.colorGroupHex},
      geometry = ${projectLayer.geometry},
      project_id = ${projectLayer.projectId},
      is_single_band = ${projectLayer.isSingleBand},
      single_band_options = ${projectLayer.singleBandOptions},
      overviews_location=${projectLayer.overviewsLocation},
      min_zoom_level=${projectLayer.minZoomLevel}
    """ ++ Fragments.whereAndOpt(Some(idFilter))).update
    query
  }

  def createProjectLayer(
      projectLayer: ProjectLayer
  ): ConnectionIO[ProjectLayer] =
    insertProjectLayer(projectLayer)

  def getProjectLayer(
      projectId: UUID,
      layerId: UUID
  ): ConnectionIO[Option[ProjectLayer]] =
    query.filter(fr"project_id = ${projectId}").filter(layerId).selectOption

  def deleteProjectLayer(
      layerId: UUID
  )(implicit L: LiftIO[ConnectionIO]): ConnectionIO[Int] =
    for {
      pl <- unsafeGetProjectLayerById(layerId)
      _ <- pl.overviewsLocation match {
        case Some(locUrl) =>
          L.liftIO(ProjectDao.removeLayerOverview(layerId, locUrl))
        case _ => ().pure[ConnectionIO]
      }
      _ <- deleteCache(layerId)
      rowsDeleted <- query.filter(layerId).delete
    } yield rowsDeleted

  def updateProjectLayer(pl: ProjectLayer, plId: UUID): ConnectionIO[Int] = {
    for {
      updateQuery <- updateProjectLayerQ(pl, plId).run
      _ <- deleteCache(plId)
    } yield updateQuery
  }

  def getDayRangeFromTimestamp(date: Timestamp): (Timestamp, Timestamp) = {
    val startOfDay =
      date.toLocalDateTime.toLocalDate.atStartOfDay
    (
      new Timestamp(startOfDay.toEpochSecond(ZoneOffset.UTC) * 1000),
      new Timestamp(
        startOfDay.plusHours(24).toEpochSecond(ZoneOffset.UTC) * 1000
      )
    )
  }

  def getWeekRangeFromTimestamp(date: Timestamp): (Timestamp, Timestamp) = {
    val week = date.toLocalDateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val year = date.toLocalDateTime.get(IsoFields.WEEK_BASED_YEAR)
    val datetimeWeek = LocalDate
      .now()
      .`with`(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
      .`with`(IsoFields.WEEK_BASED_YEAR, year)
      .`with`(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val startOfDay = datetimeWeek.atStartOfDay
    (
      new Timestamp(startOfDay.toEpochSecond(ZoneOffset.UTC) * 1000),
      new Timestamp(startOfDay.plusDays(7).toEpochSecond(ZoneOffset.UTC) * 1000)
    )
  }

  def layerIsInProject(
      layerId: UUID,
      projectID: UUID
  ): ConnectionIO[Boolean] = {
    getProjectLayerById(layerId) map {
      case Some(projectLayer) => projectLayer.projectId == Option(projectID)
      case _                  => false
    }
  }
}
