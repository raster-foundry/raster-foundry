package com.azavea.rf.database.tables

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields._
import com.azavea.rf.database.query.{ExportQueryParameters, ListQueryResult}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.params._

import cats.data._
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import slick.model.ForeignKeyAction

import java.sql.Timestamp
import java.util.UUID
import java.net.URI

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Table that represents exports
  *
  * Exports represent asynchronous export tasks to export data
  */
class Exports(_tableTag: Tag) extends Table[Export](_tableTag, "exports")
  with TimestampFields
  with OrganizationFkFields
  with UserFkFields
{
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, owner, organizationId, projectId, exportStatus,
    exportType, visibility, toolRunId, exportOptions) <> (
    Export.tupled, Export.unapply
  )

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val projectId: Rep[Option[UUID]] = column[Option[UUID]]("project_id", O.PrimaryKey)
  val exportStatus: Rep[ExportStatus] = column[ExportStatus]("export_status")
  val exportType: Rep[ExportType] = column[ExportType]("export_type")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val toolRunId: Rep[Option[UUID]] = column[Option[UUID]]("toolrun_id")
  val exportOptions: Rep[Json] = column[Json]("export_options")

  lazy val projectsFk = foreignKey("exports_project_id_fkey", projectId, Projects)(r => r.id.?, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val organizationsFk = foreignKey("exports_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("exports_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("exports_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val ownerUserFK = foreignKey("exports_owner_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val toolRunFK = foreignKey("toolrun_id", toolRunId, ToolRuns)(r => r.id.?, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object Exports extends TableQuery(tag => new Exports(tag)) with LazyLogging {

  val tq = TableQuery[Exports]
  type TableQuery = Query[Exports, Export, Seq]


  implicit class withExportsTableQuery[M, U, C[_]](exports: Exports.TableQuery) extends
    ExportTableQuery[M, U, C](exports)

  /** List exports given a page request
    *
    * @param offset Int offset of request for pagination
    * @param limit Int limit of objects per page
    * @param queryParams [[ExportQueryParameters]] query parameters for request
    */
  def listExports(offset: Int, limit: Int, queryParams: ExportQueryParameters, user: User): ListQueryResult[Export] = {
    val dropRecords = limit * offset
    val accessibleExports = Exports.filterToSharedOrganizationIfNotInRoot(user)
    ListQueryResult[Export](
      accessibleExports
        .filterByExportParams(queryParams)
        .drop(dropRecords)
        .take(limit)
        .result: DBIO[Seq[Export]],
      Exports.length.result
    )
  }

  /** Insert a upload given a create case class with a user
    *
    * @param exportToCreate [[Export.Create]] object to use to create full export
    * @param user               User to create a new export with
    */
  def insertExport(exportToCreate: Export.Create, user: User) = {
    val export = exportToCreate.toExport(user)
    (Exports returning Exports).forceInsert(export)
  }

  /** Given an export ID, attempt to retrieve it from the database
    *
    * @param exportId UUID ID of export to get from database
    */
  def getExport(exportId: UUID, user: User) =
    Exports
      .filterToSharedOrganizationIfNotInRoot(user)
      .filter(_.id === exportId)
      .result
      .headOption

  def getExportWithStatus(exportId: UUID, user: User, exportStatus: ExportStatus) =
    Exports
      .filterToSharedOrganizationIfNotInRoot(user)
      .filter(e => e.id === exportId && e.exportStatus === exportStatus)
      .result
      .headOption

  /** Given an export ID, attempt to remove it from the database
    *
    * @param exportId UUID ID of export to remove
    */
  def deleteExport(exportId: UUID, user: User) =
    Exports
      .filterToSharedOrganizationIfNotInRoot(user)
      .filter(_.id === exportId).delete

  def getScenes(export: Export, user: User)(implicit database: DB): Future[Iterable[Scene.WithRelated]] = {
    database.db.run {
      val action = Scenes
        .filter { scene =>
          scene.id in ScenesToProjects
            .filter(_.projectId === export.projectId)
            .sortBy(_.sceneOrder.asc.nullsLast)
            .map(_.sceneId)
        }
        .joinWithRelated
        .result
      logger.debug(s"Total Query for scenes -- SQL: ${action.statements.headOption}")
      action
    } map { result =>
      Scene.WithRelated.fromRecords(result)
    }
  }

  /** Export an export @param export Export to use for export
    * @param exportId UUID of export to update
    * @param user User to use to export upload
    */
  def updateExport(export: Export, exportId: UUID, user: User) = {
    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateExportQuery = for {
      updateExport <- Exports.filter(_.id === exportId)
    } yield (
      updateExport.modifiedAt,
      updateExport.modifiedBy,
      updateExport.organizationId,
      updateExport.projectId,
      updateExport.exportStatus,
      updateExport.exportType,
      updateExport.visibility,
      updateExport.exportOptions
    )

    updateExportQuery.update(
      updateTime,
      user.id,
      export.organizationId,
      export.projectId,
      export.exportStatus,
      export.exportType,
      export.visibility,
      export.exportOptions
    )
  }

  def getExportStyle(export: Export, exportOptions: ExportOptions, user: User)
                    (implicit database: DB): OptionT[Future, Either[SimpleInput, ASTInput]] = {
    (export.projectId, export.toolRunId) match {
      case (_, Some(id)) => astInput(id, export, user).map(Right(_))
      case (Some(pid), None) => {
        /* Hand-holding the type system */
        val work: Future[Option[Either[SimpleInput, ASTInput]]] =
          simpleInput(pid, export, user, exportOptions).map(si => Some(Left(si)))

        OptionT(work)
      }
      case _ => OptionT.none /* Some non-sensical combination was given */
    }
  }

  def getExportDefinition(
    export: Export,
    user: User
  )(implicit database: DB): Future[Option[ExportDefinition]] = {
    val eo: OptionT[Future, ExportOptions] = OptionT.fromOption[Future](export.getExportOptions)
    val dbxToken: OptionT[Future, Option[String]] = OptionT(Users.getDropboxAccessToken(export.owner))

    /** TODO: For some reason, OptionTs aren't working inside the for comprehension here.
      * It's a huge nuisance and they should be, given that we throw them into for comps
      * elsewhere even in this file. /shrug
      * It's a losing battle and a bit of a vanity battle for now, so leaving it ugly.
      */
    eo.value.flatMap(
      opts => {
        opts match {
          case Some(exportOpts) =>
            dbxToken.value.flatMap(
              token => {
                val outDef = OutputDefinition(
                  crs = exportOpts.getCrs,
                  rasterSize = exportOpts.rasterSize,
                  render = Some(exportOpts.render),
                  crop = exportOpts.crop,
                  stitch = exportOpts.stitch,
                  source = exportOpts.source,
                  dropboxCredential = token.getOrElse(None:Option[String])
                )
                getExportStyle(export, exportOpts, user).value.map(
                  style => {
                    style.map(
                      s => ExportDefinition(
                        export.id,
                        InputDefinition(export.projectId, exportOpts.resolution, s),
                        outDef
                      )
                    )
                  }
                )
              }
            )
          case _ =>
            throw new Exception(s"Did not find export options for export ${export.id}")
        }
      }
    )
  }

  /**
    * An AST could be given `EvalParams` that ask for scenes from the same
    * project, from different projects, or all scenes from a project. This can
    * happen at the same time, and there's nothing illegal about this, we just
    * need to make sure to include all the ingest locations.
    */
  private def astInput(
    toolRunId: UUID,
    export: Export,
    user: User
  )(implicit database: DB): OptionT[Future, ASTInput] = {

    for {
      tRun   <- OptionT(database.db.run(ToolRuns.getToolRun(toolRunId, user)))
      tool   <- OptionT(Tools.getTool(tRun.tool, user))
      oldAst <- OptionT.pure[Future, MapAlgebraAST](tool.definition.as[MapAlgebraAST].valueOr(throw _))
      subs   <- assembleSubstitutions(oldAst, { id: UUID =>
                  OptionT(Tools.getTool(id, user))
                    .map({ referrent => referrent.definition.as[MapAlgebraAST].valueOr(throw _) })
                    .value
                })
      ast    <- OptionT.fromOption[Future](oldAst.substitute(subs))
      params <- OptionT.pure[Future, EvalParams](tRun.executionParameters.as[EvalParams].valueOr(throw _))
      (scenes, projects) <- ingestLocs(params, user)
    } yield {
      ASTInput(ast, params, scenes, projects)
    }
  }

  /** Obtain the ingest locations for all Scenes and Projects which are
    * referenced in the given [[EvalParams]]. If even a single Scene anywhere is
    * found to have no `ingestLocation` value, the entire operation fails.
    */
  private def ingestLocs(
    sources: EvalParams,
    user: User
  )(implicit database: DB): OptionT[Future, (Map[UUID, String], Map[UUID, List[(UUID, String)]])] = {

    val (scenes, projects): (Stream[SceneRaster], Stream[ProjectRaster]) =
      sources.sources.toStream
        .map(_._2)
        .foldLeft((Stream.empty[SceneRaster], Stream.empty[ProjectRaster]))({
          case ((sacc, pacc), s: SceneRaster) => (s #:: sacc, pacc)
          case ((sacc, pacc), p: ProjectRaster) => (sacc, p #:: pacc)
        })

    val scenesF: OptionT[Future, Map[UUID, String]] = scenes.map({ case SceneRaster(id, _, _) =>
      OptionT(Scenes.getScene(id, user)).flatMap(s =>
        OptionT.fromOption(s.ingestLocation.map((s.id, _)))
      )
    }).sequence.map(_.toMap)

    val projectsF: OptionT[Future, Map[UUID, List[(UUID, String)]]] =
      projects.map({ case ProjectRaster(id, _, _) =>
        OptionT(ScenesToProjects.allSceneIngestLocs(id)).map((id, _))
      }).sequence.map(_.toMap)

    (scenesF |@| projectsF).map((_,_))
  }

  private def simpleInput(
    projectId: UUID,
    export: Export,
    user: User,
    exportOptions: ExportOptions
  )(implicit database: DB): Future[SimpleInput] = {
    getScenes(export, user)
      .flatMap({ iterable =>
        iterable.toList
          .map({ scene =>
            if(exportOptions.raw)
              Future(ExportLayerDefinition(scene.id, new URI(scene.ingestLocation.getOrElse("")), None))
            else
              ScenesToProjects.getColorCorrectParams(projectId, scene.id) map ({ ccp =>
                ExportLayerDefinition(scene.id, new URI(scene.ingestLocation.getOrElse("")), ccp.flatten)
              })
          })
          .sequence
          .map({ layers =>
            SimpleInput(layers.toArray, exportOptions.mask.map(_.geom))
          })
      })
  }
}

class ExportTableQuery[M, U, C[_]](exports: Exports.TableQuery) {
  def filterByExportParams(queryParams: ExportQueryParameters): Exports.TableQuery = {
    val filteredByOrganizationId = queryParams.organization match {
      case Some(org) => exports.filter(_.organizationId === org)
      case _ => exports
    }

    val filteredByProjectId = queryParams.project match {
      case Some(prj) => filteredByOrganizationId.filter(_.projectId === prj)
      case _ => filteredByOrganizationId
    }

    filteredByProjectId filter { export =>
      queryParams.exportStatus
        .map( status =>
          try {
            export.exportStatus === ExportStatus.fromString(status)
          } catch {
            case e : Exception =>
              throw new IllegalArgumentException(
                s"Invalid Ingest Status: $status"
              )
          }
        )
        .reduceLeftOption(_ || _)
        .getOrElse(true: Rep[Boolean])
    }
  }

  def page(pageRequest: PageRequest): Exports.TableQuery = {
    exports
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}
