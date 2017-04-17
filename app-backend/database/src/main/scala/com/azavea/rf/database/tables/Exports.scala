package com.azavea.rf.database.tables

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrganizationFkFields, TimestampFields}
import com.azavea.rf.database.query.{ExportQueryParameters, ListQueryResult}
import com.azavea.rf.datamodel._

import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction
import io.circe.Json
import cats.implicits._

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
{
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, organizationId, projectId, exportStatus,
    exportType, visibility, exportOptions) <> (
    Export.tupled, Export.unapply
  )

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val projectId: Rep[java.util.UUID] = column[java.util.UUID]("project_id", O.PrimaryKey)
  val exportStatus: Rep[ExportStatus] = column[ExportStatus]("export_status")
  val exportType: Rep[ExportType] = column[ExportType]("export_type")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val exportOptions: Rep[Json] = column[Json]("export_options")

  lazy val projectsFk = foreignKey("exports_project_id_fkey", projectId, Projects)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val organizationsFk = foreignKey("exports_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("exports_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("exports_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
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
    val export = exportToCreate.toExport(user.id)
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

  def getExportDefinition(export: Export, user: User)(implicit database: DB): Future[Option[ExportDefinition]] = {
    export
      .exportOptions
      .as[ExportOptions]
      .toOption
      .map { exportOptions =>
        getScenes(export, user)
          .flatMap { iterable =>
            val list = iterable.toList
            val exportLayerDefinitions: Future[ExportDefinition] =
              list
                .map { scene =>
                  ScenesToProjects.getColorCorrectParams(export.projectId, scene.id) map { ccp =>
                    ExportLayerDefinition(scene.id, new URI(scene.ingestLocation.getOrElse("")), ccp.flatten)
                  }
                }
                .sequence[Future, ExportLayerDefinition]
                .map { layers =>
                  ExportDefinition(
                    export.id,
                    input = InputDefinition(
                      projectId = export.projectId,
                      resolution = exportOptions.resolution,
                      layers = layers.toArray,
                      mask = exportOptions.mask.map(_.geom)
                    ),
                    output = OutputDefinition(
                      crs = exportOptions.getCrs,
                      rasterSize = exportOptions.rasterSize,
                      render = Some(exportOptions.render),
                      crop = exportOptions.crop,
                      stitch = exportOptions.stitch,
                      source = exportOptions.source
                    )
                  )
                }

            exportLayerDefinitions
          }
      }
      .sequence
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
