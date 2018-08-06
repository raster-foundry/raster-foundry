package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
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
import io.circe.syntax._
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
      project_id, export_status, export_type,
      visibility, toolrun_id, export_options
    FROM
  """ ++ tableF

  def insert(export: Export, user: User): ConnectionIO[Export] = {
    val insertF: Fragment = Fragment.const(s"INSERT INTO ${tableName} (")
    val ownerId = util.Ownership.checkOwner(user, Some(export.owner))
    (insertF ++fr"""
        id, created_at, created_by, modified_at, modified_by, owner,
        project_id, export_status, export_type,
        visibility, toolrun_id, export_options
      ) VALUES (
        ${UUID.randomUUID}, NOW(), ${user.id}, NOW(), ${user.id}, ${ownerId},
        ${export.projectId}, ${export.exportStatus}, ${export.exportType},
        ${export.visibility}, ${export.toolRunId}, ${export.exportOptions}
      )
    """).update.withUniqueGeneratedKeys[Export](
      "id", "created_at", "created_by", "modified_at", "modified_by", "owner",
      "project_id", "export_status", "export_type",
      "visibility", "toolrun_id", "export_options"
    )
  }

  def update(export: Export, id: UUID, user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"SET" ++ fr"""
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

    logger.info("Decoded export options successfully")

    val dropboxToken: ConnectionIO[Option[String]] = for {
      user <- UserDao.getUserById(export.owner)
    } yield {
      user.flatMap(_.dropboxCredential.token)
    }

    val outputDefinition: ConnectionIO[OutputDefinition] = for {
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

    val exportInput: Either[ConnectionIO[SimpleInput], ConnectionIO[ASTInput]] = (export.projectId, export.toolRunId) match {
      // Exporting a tool-run
      case (_, Some(toolRunId)) => Right(astInput(toolRunId, user))
      // Exporting a project
      case (Some(projectId), None) => Left(simpleInput(projectId, export, user, exportOptions))
      case (None, None) => throw new Exception(s"Export Definitions ${export.id} does not have project or ast input defined")
    }

    for {
      _ <- logger.info("Creating output definition").pure[ConnectionIO]
      outDef <- outputDefinition
      _ <- logger.info(s"Created output definition for ${outDef.source}").pure[ConnectionIO]
      _ <- logger.info("Creating input definition").pure[ConnectionIO]
      inputDefinition <- exportInput match {
        case Left(si) => {
          logger.info("In the simple input branch")
          si.map(s => InputDefinition(exportOptions.resolution, Left(s)))
        }
        case Right(asti) => {
          logger.info("In the AST input branch")
          asti.map(s => InputDefinition(exportOptions.resolution, Right(s)))
        }
      }
    } yield {
      logger.info("Created input definition")
      ExportDefinition(export.id, inputDefinition, outDef)
    }
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
      toolRun <- ToolRunDao.query.filter(toolRunId).select
      _ <- logger.info("Got tool run").pure[ConnectionIO]
      ast <- {
        toolRun.executionParameters.as[MapAlgebraAST] match {
          case Left(e) => throw e
          case Right(mapAlgebraAST) => mapAlgebraAST.withMetadata(NodeMetadata())
        }
      }.pure[ConnectionIO]
      _ <- logger.info("Fetched ast").pure[ConnectionIO]
      sceneLocs <- sceneIngestLocs(ast, user)
      _ <- logger.info("Found ingest locations for scenes").pure[ConnectionIO]
      projectLocs <- projectIngestLocs(ast, user)
      _ <- logger.info("Found ingest locations for projects").pure[ConnectionIO]
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
  """.query[ExportLayerDefinition].to[List]

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
      case s: SceneRaster => Some(s.sceneId)
      case _ => None
    }

    logger.info(s"Working with this many scenes: ${sceneIds.size}")

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
      case s: ProjectRaster => Some(s.projId)
      case _ => None
    }

    logger.info(s"Working with this many projects: ${projectIds.size}")

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
      stps <- sceneProjectSelect.query[(UUID, List[UUID], List[String])].to[List]
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

