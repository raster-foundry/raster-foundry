package com.rasterfoundry.database

import com.rasterfoundry.common.ast.codec.MapAlgebraCodec._
import com.rasterfoundry.common.ast.MapAlgebraAST._
import com.rasterfoundry.common.ast._
import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.export._
import com.rasterfoundry.database.Implicits._

import geotrellis.raster._
import geotrellis.vector.reproject.Implicits._
import geotrellis.proj4._
import cats.implicits._
import _root_.io.circe._
import _root_.io.circe.syntax._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._

import java.util.UUID

object ExportDao extends Dao[Export] {

  val tableName = "exports"

  val selectF: Fragment = fr"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      project_id, export_status, export_type,
      visibility, toolrun_id, export_options, project_layer_id
    FROM
  """ ++ tableF

  def unsafeGetExportById(exportId: UUID): ConnectionIO[Export] =
    query.filter(exportId).select

  def getExportById(exportId: UUID): ConnectionIO[Option[Export]] =
    query.filter(exportId).selectOption

  def insert(export: Export, user: User): ConnectionIO[Export] = {
    val insertF: Fragment = Fragment.const(s"INSERT INTO ${tableName} (")
    val ownerId = util.Ownership.checkOwner(user, Some(export.owner))
    (insertF ++ fr"""
        id, created_at, created_by, modified_at, modified_by, owner,
        project_id, export_status, export_type,
        visibility, toolrun_id, export_options, project_layer_id
      ) VALUES (
        ${UUID.randomUUID}, NOW(), ${user.id}, NOW(), ${user.id}, ${ownerId},
        ${export.projectId}, ${export.exportStatus}, ${export.exportType},
        ${export.visibility}, ${export.toolRunId}, ${export.exportOptions},
        ${export.projectLayerId}
      )
    """).update.withUniqueGeneratedKeys[Export](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "owner",
      "project_id",
      "export_status",
      "export_type",
      "visibility",
      "toolrun_id",
      "export_options",
      "project_layer_id"
    )
  }

  def update(export: Export, id: UUID, user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"SET" ++ fr"""
        modified_at = NOW(),
        modified_by = ${user.id},
        export_status = ${export.exportStatus},
        visibility = ${export.visibility}
      WHERE id = ${id}
    """).update.run
  }

  def getWithStatus(id: UUID,
                    status: ExportStatus): ConnectionIO[Option[Export]] = {
    (selectF ++ fr"WHERE id = ${id} AND export_status = ${status}")
      .query[Export]
      .option
  }

  def getExportDefinition(export: Export, user: User): ConnectionIO[Json] = {
    val exportOptions = export.exportOptions.as[ExportOptions] match {
      case Left(e) =>
        throw new Exception(
          s"Did not find options for export ${export.id}: ${e}")
      case Right(eo) => eo
    }

    logger.debug("Decoded export options successfully")

    val outputDefinition: ConnectionIO[OutputDefinition] = for {
      user <- UserDao.getUserById(export.owner)
    } yield {
      OutputDefinition(
        crs = exportOptions.getCrs,
        destination = exportOptions.source.toString,
        dropboxCredential = user.flatMap(_.dropboxCredential.token)
      )
    }

    val exportSource: ConnectionIO[Json] = {

      logger.info(s"Project id when getting input style: ${export.projectId}")
      logger.info(s"Tool run id when getting input style: ${export.toolRunId}")
      (export.projectId, export.projectLayerId, export.toolRunId) match {
        // Exporting a tool-run
        case (_, _, Some(toolRunId)) =>
          astInput(toolRunId, exportOptions).map(_.asJson)
        // Exporting a project layer
        case (Some(projectId), Some(projectLayerId), None) =>
          for {
            isLayerInProject <- ProjectLayerDao.layerIsInProject(projectLayerId, projectId)
            mosaicExportSourceList <- isLayerInProject match {
              case true =>
                mosaicInput(projectLayerId, exportOptions).map(_.asJson)
              case false =>
                throw new Exception(s"Layer ${projectLayerId} is not in project ${projectId}")
            }
          } yield { mosaicExportSourceList }
        // Exporting a project
        case (Some(projectId), None, None) =>
          for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            mosaicExportSourceList <- mosaicInput(project.defaultLayerId, exportOptions).map(_.asJson)
          } yield { mosaicExportSourceList }
        case (None, None, None) =>
          throw new Exception(
            s"Export Definitions ${export.id} does not have project or ast input defined")
      }
    }

    for {
      _ <- logger.info("Creating output definition").pure[ConnectionIO]
      outputDef <- outputDefinition
      _ <- logger
        .info(s"Created output definition for ${exportOptions.source}")
        .pure[ConnectionIO]
      _ <- logger.info(s"Creating input definition").pure[ConnectionIO]
      sourceDef <- exportSource
      _ <- logger.info("Created input definition").pure[ConnectionIO]
    } yield {
      Json.obj("id" -> export.id.asJson,
               "src" -> sourceDef,
               "output" -> outputDef.asJson)
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
      exportOptions: ExportOptions
  ): ConnectionIO[AnalysisExportSource] = {
    for {
      toolRun <- ToolRunDao.query.filter(toolRunId).select
      _ <- logger.debug("Got tool run").pure[ConnectionIO]
      oldAST <- {
        toolRun.executionParameters.as[MapAlgebraAST] match {
          case Right(mapAlgebraAST) => mapAlgebraAST
          case Left(e)              => throw e
        }
      }.pure[ConnectionIO]
      _ <- logger.debug("Fetched ast").pure[ConnectionIO]
      projectLocs <- projectIngestLocs(oldAST)
      _ <- logger
        .debug("Found ingest locations for projects")
        .pure[ConnectionIO]
    } yield {
      val mamlExpression = MamlConversion.fromDeprecatedAST(oldAST)
      exportOptions.mask.map(_.geom.reproject(WebMercator, LatLng)) match {
        case Some(mask) =>
          AnalysisExportSource(
            exportOptions.resolution,
            mask,
            mamlExpression,
            projectLocs
          )
        case None =>
          throw new Exception("Exports are required to provide a mask")
      }
    }
  }

  private def mosaicInput(
      layerId: UUID,
      exportOptions: ExportOptions
  ): ConnectionIO[MosaicExportSource] = {
    SceneToLayerDao.getMosaicDefinition(layerId).compile.toList map { mds =>
      // we definitely need NoData but it isn't obviously available :(
      val ndOverride: Option[Double] = None
      val layers = mds.map { md =>
        md.ingestLocation.map { location =>
          (location,
           exportOptions.bands.map(_.toList).getOrElse(List()),
           ndOverride)
        }
      }.flatten
      exportOptions.mask.map(_.geom.reproject(WebMercator, LatLng)) match {
        case Some(mask) =>
          MosaicExportSource(
            exportOptions.resolution,
            mask,
            layers
          )
        case None =>
          throw new Exception("Exports are required to provide a mask")
      }
    }
  }

  private def stripMetadata(ast: MapAlgebraAST): MapAlgebraAST = ast match {
    case astLeaf: MapAlgebraAST.MapAlgebraLeaf =>
      astLeaf.withMetadata(NodeMetadata())
    case astNode: MapAlgebraAST =>
      val args = ast.args.map(stripMetadata)
      ast.withMetadata(NodeMetadata()).withArgs(args)
  }

  // Returns ID as string and a list of location/band/ndoverride
  private def projectIngestLocs(ast: MapAlgebraAST)
    : ConnectionIO[Map[String, List[(String, Int, Option[Double])]]] = {
    val projToIngestLoc: Map[UUID, (UUID, Int, Option[Double])] =
      ast.tileSources.flatMap {
        case s: ProjectRaster =>
          val projectId = s.projId
          val nodeId = s.id
          val band = s.band.getOrElse(1)
          // This really, really needs to be fixed upstream.
          val ndOverride = s.celltype.flatMap {
            case BitCellType                         => None
            case ByteCellType                        => None
            case UByteCellType                       => None
            case ShortCellType                       => None
            case UShortCellType                      => None
            case IntCellType                         => None
            case FloatCellType                       => None
            case DoubleCellType                      => None
            case ByteConstantNoDataCellType          => Some(Byte.MinValue.toDouble)
            case UByteConstantNoDataCellType         => Some(0.toDouble)
            case ShortConstantNoDataCellType         => Some(Short.MinValue.toDouble)
            case UShortConstantNoDataCellType        => Some(0.toDouble)
            case IntConstantNoDataCellType           => Some(Int.MinValue.toDouble)
            case FloatConstantNoDataCellType         => Some(Double.NaN)
            case DoubleConstantNoDataCellType        => Some(Double.NaN)
            case ByteUserDefinedNoDataCellType(nd)   => Some(nd.toDouble)
            case UByteUserDefinedNoDataCellType(nd)  => Some(nd.toDouble)
            case ShortUserDefinedNoDataCellType(nd)  => Some(nd.toDouble)
            case UShortUserDefinedNoDataCellType(nd) => Some(nd.toDouble)
            case IntUserDefinedNoDataCellType(nd)    => Some(nd.toDouble)
            case FloatUserDefinedNoDataCellType(nd)  => Some(nd.toDouble)
            case DoubleUserDefinedNoDataCellType(nd) => Some(nd)
          }
          Some(projectId -> (nodeId, band, ndOverride))
        case _ =>
          None
      }.toMap

    logger.debug(s"Working with this many projects: ${projToIngestLoc.size}")

    val sceneProjectSelect = fr"""
    SELECT stp.project_id, array_agg(stp.scene_id), array_agg(scenes.ingest_location)
    FROM
      scenes_to_projects as stp
    LEFT JOIN
      scenes
    ON
      stp.scene_id = scenes.id
    """ ++ Fragments.whereAndOpt(
      projToIngestLoc.keys.toList.toNel.map(ids =>
        Fragments.in(fr"stp.project_id", ids))
    ) ++ fr"GROUP BY stp.project_id"
    val projectSceneLocs = for {
      stps <- sceneProjectSelect
        .query[(UUID, List[UUID], List[String])]
        .to[List]
    } yield {
      stps.flatMap {
        case (pID, sID, loc) =>
          if (loc.isEmpty) {
            None
          } else {
            Some(
              s"${pID}_${projToIngestLoc(pID)._2}" ->
                loc.map({
                  (_, projToIngestLoc(pID)._2, projToIngestLoc(pID)._3)
                }))
          }
      }.toMap
    }
    projectSceneLocs
  }
}
