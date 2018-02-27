package com.azavea.rf.database

import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast._
import MapAlgebraAST._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}
import java.net.URI


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

  def getExportDefinition(id: UUID, user: User): ConnectionIO[ExportDefinition] = ???

  def getExportStyle(export: Export, exportOptions: ExportOptions, user: User): OptionT[Future, Either[SimpleInput, ASTInput]] = {
    // (export.projectId, export.toolRunId) match {
    //   // Exporting a tool-run
    //   case (_, Some(id)) => astInput(id, user).map(Right(_))
    //   // Exporting a project
    //   case (Some(id), None) => {
    //     val work: Future[Option[Either[SimpleInput, ASTInput]]] =
    //       simpleInput(pid, export, user, exportOptions).map(si => Some(Left(si)))
    //     OptionT(work)
    //   }
    //   // Invalid
    //   case _ => OptionT.none
    // }
    ???
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
  ): OptionT[Future, ASTInput] ={
    // for {
    //   tRun   <- OptionT(database.db.run(ToolRuns.getToolRun(toolRunId, user)))
    //   ast    <- OptionT.pure[Future](tRun.executionParameters.as[MapAlgebraAST].valueOr(throw _))
    //   (scenes, projects) <- ingestLocs(ast, user)
    // } yield {
    //   ASTInput(ast, scenes, projects)
    // }
    ???
  }

  private def simpleInput(
    projectId: UUID,
    export: Export,
    user: User,
    exportOptions: ExportOptions
  ): Future[SimpleInput] = {
    val scenesWithRelated = SceneWithRelatedDao.query
      .filter(fr"""
        scenes.id IN (
          SELECT scene_id
          FROM scenes_to_projects
          WHERE project_id = ${projectId}
        )
      """).list

    scenesWithRelated.flatMap(scenes => {
      val exportDef: = (for {
        scene <- scenes
        colorParams <- SceneToProjectDao.getColorCorrectParams(projectId, scene.id)
        exportDef <- {
          if (exportOptions.raw) {
            ExportLayerDefinition(scene.id, new URI(scene.ingestLocation.getOrElse("")), None)
          } else {
            ExportLayerDefinition(scene.id, new URI(scene.ingestLocation.getOrElse("")), Some(colorParams))
          }
        }
      } yield exportDef)
      exportDef.map(layers => SimpleInput(layers.toArray, exportOptions.mask.map(_.geom)))
    })
  }

  /** Obtain the ingest locations for all Scenes and Projects which are
    * referenced in the given [[EvalParams]]. If even a single Scene anywhere is
    * found to have no `ingestLocation` value, the entire operation fails.
    *
    * @note Scenes are represented with a map from scene ID to ingest location.
    * @note Projects are represented with a map from project ID to a map from scene ID to
    *       ingest location
    */
  private def ingestLocs(
    ast: MapAlgebraAST,
    user: User
  ): OptionT[Future, (Map[UUID, String], Map[UUID, List[(UUID, String)]])] = {

    // val (scenes, projects): (Stream[SceneRaster], Stream[ProjectRaster]) =
    //   ast.tileSources
    //     .toStream
    //     .foldLeft((Stream.empty[SceneRaster], Stream.empty[ProjectRaster]))({
    //       case ((sacc, pacc), s: SceneRaster) => (s #:: sacc, pacc)
    //       case ((sacc, pacc), p: ProjectRaster) => (sacc, p #:: pacc)
    //       case ((sacc, pacc), _) => (sacc, pacc)
    //     })

    // val scenesF: OptionT[Future, Map[UUID, String]] =
    //   scenes.map({ case SceneRaster(_, sceneId, _, _, _) =>
    //     OptionT(Scenes.getScene(sceneId, user)).flatMap(s =>
    //       OptionT.fromOption(s.ingestLocation.map((s.id, _)))
    //     )
    //   }).sequence.map(_.toMap)

    // val projectsF: OptionT[Future, Map[UUID, List[(UUID, String)]]] =
    //   projects.map({ case ProjectRaster(id, projId, _, _, _) =>
    //     OptionT(ScenesToProjects.allSceneIngestLocs(id)).map((id, _))
    //   }).sequence.map(_.toMap)

    // (scenesF, projectsF).mapN((_,_))
    ???
  }
}

