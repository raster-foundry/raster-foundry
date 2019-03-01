package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.database.Implicits._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest
import cats.implicits._
import java.sql.Timestamp
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

import cats.free.Free
import doobie.free.connection

import scala.collection.immutable

object ProjectLayerDao extends Dao[ProjectLayer] {
  val tableName = "project_layers"

  val selectF: Fragment =
    fr"SELECT id, created_at, modified_at, name, project_id, color_group_hex, smart_layer_id, range_start, range_end, geometry, is_single_band, single_band_options from" ++ tableF

  def unsafeGetProjectLayerById(
      projectLayerId: UUID): ConnectionIO[ProjectLayer] = {
    query.filter(projectLayerId).select
  }

  def listProjectLayersForProject(
      page: PageRequest,
      projectId: UUID): ConnectionIO[PaginatedResponse[ProjectLayer]] = {
    query
      .filter(fr"project_id = ${projectId}")
      .page(page)

  }

  def insertProjectLayer(
      pl: ProjectLayer
  ): ConnectionIO[ProjectLayer] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
    (id, created_at, modified_at, name, project_id, color_group_hex,
    smart_layer_id, range_start, range_end, geometry, is_single_band, single_band_options)
    VALUES
      (${pl.id}, ${pl.createdAt}, ${pl.modifiedAt}, ${pl.name}, ${pl.projectId},
      ${pl.colorGroupHex}, ${pl.smartLayerId}, ${pl.rangeStart}, ${pl.rangeEnd},
      ${pl.geometry}, ${pl.isSingleBand}, ${pl.singleBandOptions})
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
      "single_band_options"
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
      single_band_options = ${projectLayer.singleBandOptions}
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

  def deleteProjectLayer(layerId: UUID): ConnectionIO[Int] =
    for {
      deleteCount <- query.filter(layerId).delete
    } yield deleteCount

  def updateProjectLayer(pl: ProjectLayer, plId: UUID): ConnectionIO[Int] = {
    updateProjectLayerQ(pl, plId).run
  }

  def getLayerScenes(
      layerId: UUID,
      splitOptions: SplitOptions): ConnectionIO[List[Scene.ProjectScene]] =
    ProjectLayerScenesDao.listLayerScenesRaw(layerId, splitOptions)

  def batchCreateLayers(
      groupedScenes: Map[(Option[(Timestamp, Timestamp)], Option[String]),
                         List[Scene.ProjectScene]],
      layer: ProjectLayer,
      splitOptions: SplitOptions): ConnectionIO[List[ProjectLayer]] = {
    val projectLayersAndScenes
      : Map[ProjectLayer.Create, List[Scene.ProjectScene]] = groupedScenes.map {
      case ((Some((start, end)), datasourceO), scenes) =>
        (ProjectLayer.Create(
           datasourceO match {
             case Some(datasource) =>
               s"${splitOptions.name} | " +
                 s"${datasource}"
             case _ =>
               s"${splitOptions.name}"
           },
           layer.projectId,
           splitOptions.colorGroupHex.getOrElse(layer.colorGroupHex),
           Some(layer.id),
           Some(start),
           Some(end),
           layer.geometry,
           layer.isSingleBand,
           layer.singleBandOptions
         ),
         scenes)
      case ((_, datasourceO), scenes) =>
        (ProjectLayer.Create(
           datasourceO match {
             case Some(datasource) =>
               s"${splitOptions.name} | " +
                 s"${datasource}"
             case _ =>
               s"${splitOptions.name}"
           },
           layer.projectId,
           splitOptions.colorGroupHex.getOrElse(layer.colorGroupHex),
           Some(layer.id),
           None,
           None,
           layer.geometry,
           layer.isSingleBand,
           layer.singleBandOptions
         ),
         scenes)
    }
    val createdProjectLayers = projectLayersAndScenes.toList traverse {
      case (projectLayerC: ProjectLayer.Create,
            scenes: List[Scene.ProjectScene]) =>
        (scenes.toNel, layer.projectId) match {
          case (Some(s), Some(pId)) =>
            for {
              insertedLayer <- insertProjectLayer(projectLayerC.toProjectLayer)
              _ <- ProjectDao.addScenesToProject(
                s.map(_.id),
                pId,
                true,
                Some(insertedLayer.id)
              )
            } yield insertedLayer
          case _ =>
            throw new java.lang.IllegalArgumentException(
              s"Cannot add scenes to a layer which is not in a project: ${layer.id}"
            )
        }
    }
    createdProjectLayers
  }

  def getDayRangeFromTimestamp(date: Timestamp): (Timestamp, Timestamp) = {
    val startOfDay =
      date.toLocalDateTime.toLocalDate.atStartOfDay
    (new Timestamp(startOfDay.toEpochSecond(ZoneOffset.UTC) * 1000),
     new Timestamp(
       startOfDay.plusHours(24).toEpochSecond(ZoneOffset.UTC) * 1000))
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
    (new Timestamp(startOfDay.toEpochSecond(ZoneOffset.UTC) * 1000),
     new Timestamp(startOfDay.plusDays(7).toEpochSecond(ZoneOffset.UTC) * 1000))
  }

  def groupScenesBySplitOptions(splitOptions: SplitOptions)
    : Scene.ProjectScene => (Option[(Timestamp, Timestamp)], Option[String]) = {
    scene: Scene.ProjectScene =>
      (splitOptions.period, splitOptions.splitOnDatasource) match {
        case (SplitPeriod.Day, Some(true)) =>
          (scene.filterFields.acquisitionDate.map(getDayRangeFromTimestamp),
           Some(scene.datasource.name))
        case (SplitPeriod.Week, Some(true)) =>
          (scene.filterFields.acquisitionDate.map(getWeekRangeFromTimestamp),
           Some(scene.datasource.name))
        case (SplitPeriod.Day, _) =>
          (scene.filterFields.acquisitionDate.map(getDayRangeFromTimestamp),
           None)
        case (SplitPeriod.Week, _) =>
          (scene.filterFields.acquisitionDate.map(getWeekRangeFromTimestamp),
           None)
      }
  }

  def splitProjectLayer(projectId: UUID,
                        layerId: UUID,
                        splitOptions: SplitOptions,
                        user: User): ConnectionIO[List[ProjectLayer]] = {
    // TODO migration to rename smart_layer_id to parent_layer_id `with` dependency on itself
    for {
      layer <- unsafeGetProjectLayerById(layerId)
      scenes <- getLayerScenes(layerId, splitOptions)
      groupedScenes = scenes.groupBy(groupScenesBySplitOptions(splitOptions))
      newLayers <- batchCreateLayers(groupedScenes, layer, splitOptions)
      _ <- splitOptions.removeFromLayer match {
        case Some(true) =>
          ProjectDao.deleteScenesFromProject(scenes.map(_.id),
                                             projectId,
                                             Some(layerId))
        case _ => 0.pure[ConnectionIO]
      }
    } yield newLayers
  }

  def layerIsInProject(layerId: UUID,
                       projectID: UUID): ConnectionIO[Boolean] = {
    query.filter(layerId).selectOption map {
      case Some(projectLayer) => projectLayer.projectId == Option(projectID)
      case _                  => false
    }
  }
}
