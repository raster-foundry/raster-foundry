package com.azavea.rf.database

import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast._
import cats.implicits._
import MapAlgebraAST._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import io.circe._
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}
import java.net.URI

import cats.free.Free
import doobie.free.connection


object ExportDao extends Dao[Export] {

  val tableName = "exports"

  val selectF = fr"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, project_id, export_status, export_type,
      visibility, toolrun_id, export_options
    FROM
  """ ++ tableF

  def insert(export: Export, user: User): ConnectionIO[Export] = {
    val ownerId = util.Ownership.checkOwner(user, Some(export.owner))
    (fr"""
      INSERT INTO ${tableName} (
        id, created_at, created_by, modified_at, modified_by, owner,
        organization_id, project_id, export_status, export_type,
        visibility, toolrun_id, export_options
      ) VALUES (
        ${UUID.randomUUID}, NOW(), ${user.id}, NOW(), ${user.id}, ${ownerId},
        ${export.organizationId}, ${export.projectId}, ${export.exportStatus}, ${export.exportType},
        ${export.visibility}, ${export.toolRunId}, ${export.exportOptions}
      )
    """).update.withUniqueGeneratedKeys[Export](
      "id", "created_at", "created_by", "modified_at", "modified_by", "owner",
      "organization_id", "project_id", "export_status", "export_type",
      "visibility", "toolrun_id", "export_options"
    )
  }

  def update(export: Export, id: UUID, user: User): ConnectionIO[Int] = {
    (fr"""
      UPDATE ${tableName} SET
        modified_at = NOW(),
        modified_by = ${user.id},
        export_status = ${export.exportStatus},
        visibility = ${export.visibility}
      WHERE id = ${id} AND owner = ${user.id}
    """).update.run
  }

  def getWithStatus(id: UUID, user: User, status: ExportStatus): ConnectionIO[Option[Export]] = {
    (selectF ++ fr"WHERE id = ${id} AND export_status = ${status}").query[Export].option
  }

  def getExportDefinition(export: Export, user: User): ConnectionIO[ExportDefinition] = {
    val exportOptions = export.exportOptions.as[ExportOptions] match {
      case Left(e) => throw new Exception(s"Did not find options for export ${export.id}: ${e}")
      case Right(eo) => eo
    }

    val dropboxToken = for {
      user <- UserDao.query.filter(fr"id = ${user.id}").selectOption
    } yield {
      user.flatMap(_.dropboxCredential)
    }

    val outputDefinition = for {
      dbxToken <- dropboxToken
    } yield {
      OutputDefinition(
        crs = exportOptions.getCrs,
        rasterSize = exportOptions.rasterSize,
        render = Some(exportOptions.render),
        crop = exportOptions.crop,
        stitch = exportOptions.stitch,
        source = exportOptions.source,
        dropboxCredential = dbxToken
      )
    }

    val exportInput = (export.projectId, export.toolRunId) match {
      // Exporting a tool-run
      case (_, Some(toolRunId)) => Right(astInput(toolRunId, user))
      // Exporting a project
      case (Some(projectId), None) => Left(simpleInput(projectId, export, user, exportOptions))
      case (None, None) => throw new Exception(s"Export Definitions ${export.id} does not have project or ast input defined")
    }

    for {
      outDef <- outputDefinition
      inputDefinition <- exportInput match {
        case Left(si) => si.map(s => InputDefinition(export.projectId, exportOptions.resolution, Left(s)))
        case Right(asti) => asti.map(s => InputDefinition(export.projectId, exportOptions.resolution, Right(s)))
      }
    } yield ExportDefinition(export.id, inputDefinition, outDef)
  }


  /**
    * An AST could include nodes that ask for scenes from the same
    * project, from different projects, or all scenes from a project. This can
    * happen at the same time, and there's nothing illegal about this, we just
    * need to make sure to include all the ingest locations.
    */
  private def astInput(
    toolRunId: UUID,
    user: User
  ): ConnectionIO[ASTInput] ={
    for {
      toolRun <- ToolRunDao.query.filter(toolRunId).ownerFilter(user).select
      ast <- {
        toolRun.executionParameters.as[MapAlgebraAST] match {
          case Left(e) => throw e
          case Right(mapAlgebraAST) => mapAlgebraAST
        }
      }.pure[ConnectionIO]
      sceneLocs <- sceneIngestLocs(ast, user)
      projectLocs <- projectIngestLocs(ast, user)
    } yield {
      ASTInput(ast, sceneLocs, projectLocs)
    }
  }

  private def simpleInput(
    projectId: UUID,
    export: Export,
    user: User,
    exportOptions: ExportOptions
  ): ConnectionIO[SimpleInput] = {

    val exportLayerDefinitions = fr"""
    SELECT scenes.id, scenes.ingest_location, stp.mosaic_definition
    FROM
      scenes
    LEFT JOIN
      scenes_to_projects stp
    ON
      stp.scene_id = scenes.id
    WHERE
      stp.project_id = ${projectId}
  """.query[ExportLayerDefinition].list

    for {
      layerDefinitions <- exportLayerDefinitions
    } yield {
      val modifiedLayerDefinitions = layerDefinitions.map { eld =>
        if (exportOptions.raw) {
          eld.copy(colorCorrections = None)
        } else {
          eld
        }
      }
      SimpleInput(modifiedLayerDefinitions.toArray, exportOptions.mask.map(_.geom))
    }
  }

  /** Obtain the ingest locations for all Scenes and Projects which are
    * referenced in the given [[EvalParams]]. If even a single Scene anywhere is
    * found to have no `ingestLocation` value, the entire operation fails.
    *
    * @note Scenes are represented with a map from scene ID to ingest location.
    * @note Projects are represented with a map from project ID to a map from scene ID to
    *       ingest location
    */
  private def sceneIngestLocs(
    ast: MapAlgebraAST,
    user: User
  ): ConnectionIO[Map[UUID, String]] = {

    val sceneIds: Set[UUID] = ast.tileSources.flatMap {
      case s: SceneRaster => Some(s.id)
      case _ => None
    }

    for {
      scenes <- SceneDao.query.filter(sceneIds.toList.toNel.map(ids => Fragments.in(fr"id", ids))).list
    } yield {
      scenes.flatMap{ scene =>
        scene.ingestLocation.map((scene.id, _))
      }.toMap
    }
  }

  private def projectIngestLocs(ast: MapAlgebraAST, user: User): ConnectionIO[Map[UUID, List[(UUID, String)]]] = {
    val projectIds: Set[UUID] = ast.tileSources.flatMap {
      case s: ProjectRaster => Some(s.id)
      case _ => None
    }

    val sceneProjectSelect = fr"""
    SELECT stp.project_id, array_agg(stp.scene_id), array_agg(scenes.ingest_location)
    FROM
      scenes_to_projects as stp
    LEFT JOIN
      scenes
    ON
      stp.scene_id = scenes.id
    """ ++ Fragments.whereAndOpt(projectIds.toList.toNel.map(ids => Fragments.in(fr"stp.project_id", ids))) ++ fr"GROUP BY stp.project_id"
    val projectSceneLocs = for {
      stps <- sceneProjectSelect.query[(UUID, List[UUID], List[String])].list
    } yield {
      stps.flatMap{ case (pID, sID, loc) =>
        if (loc.isEmpty) {
          None
        } else {
          Some((pID, sID zip loc))
        }
      }.toMap
    }
    projectSceneLocs
  }
}

