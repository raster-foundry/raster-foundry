package com.rasterfoundry.database

import com.rasterfoundry.common.ast.MapAlgebraAST._
import com.rasterfoundry.common.ast._
import com.rasterfoundry.common.ast.codec.MapAlgebraCodec._
import com.rasterfoundry.common.export._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import _root_.io.circe._
import _root_.io.circe.syntax._
import cats.data.NonEmptyList
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.vector.reproject.Implicits._

import java.util.UUID

object ExportDao extends Dao[Export] {

  val tableName = "exports"

  val selectF: Fragment = fr"""
    SELECT
      id, created_at, created_by, modified_at, owner,
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
        id, created_at, created_by, modified_at, owner,
        project_id, export_status, export_type,
        visibility, toolrun_id, export_options, project_layer_id
      ) VALUES (
        ${UUID.randomUUID}, NOW(), ${user.id}, NOW(), ${ownerId},
        ${export.projectId}, ${export.exportStatus}, ${export.exportType},
        ${export.visibility}, ${export.toolRunId}, ${export.exportOptions},
        ${export.projectLayerId}
      )
    """).update.withUniqueGeneratedKeys[Export](
      "id",
      "created_at",
      "created_by",
      "modified_at",
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

  def update(export: Export, id: UUID): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"SET" ++ fr"""
        modified_at = NOW(),
        export_status = ${export.exportStatus},
        visibility = ${export.visibility}
      WHERE id = ${id}
    """).update.run
  }

  def getWithStatus(
      id: UUID,
      status: ExportStatus
  ): ConnectionIO[Option[Export]] = {
    (selectF ++ fr"WHERE id = ${id} AND export_status = ${status}")
      .query[Export]
      .option
  }

  def getExportDefinition(export: Export): ConnectionIO[Json] = {
    val exportOptions = export.exportOptions.as[ExportOptions] match {
      case Left(e) =>
        throw new Exception(
          s"Did not find options for export ${export.id}: ${e}"
        )
      case Right(eo) => eo
    }

    logger.debug("Decoded export options successfully")

    val outputDefinition: ConnectionIO[OutputDefinition] = for {
      userMaybePlatformId <- UserDao.getUserById(export.owner)
    } yield {
      OutputDefinition(
        crs = exportOptions.getCrs,
        destination = exportOptions.source.toString,
        dropboxCredential =
          userMaybePlatformId.flatMap(_._1.dropboxCredential.token)
      )
    }

    val exportSource: ConnectionIO[Json] = {

      logger.debug(s"Project id when getting input style: ${export.projectId}")
      logger.debug(s"Tool run id when getting input style: ${export.toolRunId}")
      (export.projectId, export.projectLayerId, export.toolRunId) match {
        // Exporting a tool-run
        case (_, _, Some(toolRunId)) =>
          astInput(toolRunId, exportOptions).map(_.asJson)
        // Exporting a project layer
        case (Some(projectId), Some(projectLayerId), None) =>
          for {
            isLayerInProject <- ProjectLayerDao.layerIsInProject(
              projectLayerId,
              projectId
            )
            mosaicExportSourceList <- isLayerInProject match {
              case true =>
                mosaicInput(projectLayerId, exportOptions).map(_.asJson)
              case false =>
                throw new Exception(
                  s"Layer ${projectLayerId} is not in project ${projectId}"
                )
            }
          } yield { mosaicExportSourceList }
        // Exporting a project
        case (Some(projectId), None, None) =>
          for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            mosaicExportSourceList <- mosaicInput(
              project.defaultLayerId,
              exportOptions
            ).map(_.asJson)
          } yield { mosaicExportSourceList }
        case (None, Some(projectLayerId), None) =>
          throw new Exception(
            s"Export Definitions ${export.id} does not have a project id to export layer ${projectLayerId}"
          )
        case (None, None, None) =>
          throw new Exception(
            s"Export Definitions ${export.id} does not have project or ast input defined"
          )
      }
    }

    for {
      _ <- logger.debug("Creating output definition").pure[ConnectionIO]
      outputDef <- outputDefinition
      _ <- logger
        .info(s"Created output definition for ${exportOptions.source}")
        .pure[ConnectionIO]
      _ <- logger.debug(s"Creating input definition").pure[ConnectionIO]
      sourceDef <- exportSource
      _ <- logger.debug("Created input definition").pure[ConnectionIO]
    } yield {
      Json.obj(
        "id" -> export.id.asJson,
        "src" -> sourceDef,
        "output" -> outputDef.asJson
      )
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
      exportParameters <- getExportParameters(oldAST)
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
            exportParameters
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
    SceneToLayerDao.getMosaicDefinition(layerId) map { mds =>
      // we definitely need NoData but it isn't obviously available :(
      val ndOverride: Option[Double] = None
      val layers = mds.flatMap { md =>
        md.ingestLocation.map { location =>
          (
            location,
            exportOptions.bands.map(_.toList).getOrElse(List()),
            ndOverride
          )
        }
      }
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

  private def stripMetadata(ast: MapAlgebraAST): MapAlgebraAST =
    ast match {
      case astLeaf: MapAlgebraAST.MapAlgebraLeaf =>
        astLeaf.withMetadata(NodeMetadata())
      case _: MapAlgebraAST =>
        val args = ast.args.map(stripMetadata)
        ast.withMetadata(NodeMetadata()).withArgs(args)
    }

  private def getProjectLocations(
      projects: NonEmptyList[UUID]
  ): ConnectionIO[Map[UUID, (List[UUID], List[String])]] = {
    val q =
      sql"""SELECT projects.id, array_agg(stl.scene_id), array_agg(scenes.ingest_location)
    FROM
    projects
    LEFT JOIN
    scenes_to_layers as stl
    ON
    projects.default_layer_id = stl.project_layer_id
    LEFT JOIN
    scenes
    ON
    stl.scene_id = scenes.id
    """ ++ Fragments.whereAndOpt(
        Some(Fragments.in(fr"projects.id", projects)),
        Some(fr"scenes.ingest_location IS NOT null")
      ) ++
        fr"GROUP BY projects.id"
    for {
      projectSceneLocations <- q
        .query[(UUID, List[UUID], List[String])]
        .to[List]
    } yield {
      projectSceneLocations.map {
        case (projectId, sceneIds, locations) =>
          (projectId, (sceneIds, locations))
      }.toMap
    }
  }

  private def getLayerLocations(
      layers: NonEmptyList[UUID]
  ): ConnectionIO[Map[UUID, (List[UUID], List[String])]] = {
    val q: Fragment =
      sql"""SELECT stl.project_layer_id, array_agg(stl.scene_id), array_agg(scenes.ingest_location)
    FROM
    scenes_to_layers as stl
    LEFT JOIN
    scenes
    ON
    stl.scene_id = scenes.id
    """ ++ Fragments.whereAndOpt(
        Some(Fragments.in(fr"stl.project_layer_id", layers)),
        Some(fr"scenes.ingest_location IS NOT null")
      ) ++
        fr"GROUP BY stl.project_layer_id"
    for {
      projectLayerSceneLocations <- q
        .query[(UUID, List[UUID], List[String])]
        .to[List]
    } yield {
      projectLayerSceneLocations.map {
        case (layerId, sceneIds, locations) =>
          (layerId, (sceneIds, locations))
      }.toMap
    }
  }

  sealed trait SourceType
  final case object ProjectSrc extends SourceType
  final case object LayerSrc extends SourceType

  final case class Parameters(
      id: UUID,
      sourceType: SourceType,
      band: Int,
      nodataOverride: Option[Double]
  )

  // Returns ID as string and a list of location/band/ndoverride
  private def getExportParameters(
      ast: MapAlgebraAST
  ): ConnectionIO[Map[String, List[(String, Int, Option[Double])]]] = {

    // This really, really needs to be fixed upstream.
    def setNoDataOverride(cellType: CellType): Option[Double] = {
      cellType match {
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
    }

    val parameters: Set[Parameters] = {
      logger.debug(s"AST TILE SOURCES: ${ast.tileSources}")
      logger.debug(s"AST ${ast.asJson}")

      ast.tileSources.flatMap { s =>
        s match {
          case s: ProjectRaster =>
            val projectId = s.projId
            val band = s.band.getOrElse(1)
            val ndOverride = s.celltype.flatMap(setNoDataOverride)
            Some(Parameters(projectId, ProjectSrc, band, ndOverride))
          case s: LayerRaster =>
            val layerId = s.layerId
            val band = s.band.getOrElse(1)
            val ndOverride = s.celltype.flatMap(setNoDataOverride)
            Some(Parameters(layerId, LayerSrc, band, ndOverride))
          case _ =>
            None
        }
      }
    }
    logger.debug(s"Parameters: ${parameters}")
    logger.debug(s"Working with this many parameters: ${parameters.size}")

    val projectIds =
      parameters
        .filter(_.sourceType match {
          case _: ProjectSrc.type => true
          case _                  => false
        })
        .map(_.id)
        .toList
        .toNel
    val layerIds =
      parameters
        .filter(_.sourceType match {
          case _: LayerSrc.type => true
          case _                => false
        })
        .map(_.id)
        .toList
        .toNel

    val sceneLocations = for {
      projectLocations <- {
        projectIds match {
          case Some(ids) => getProjectLocations(ids)
          case _         => Map[UUID, (List[UUID], List[String])]().pure[ConnectionIO]
        }
      }
      layerLocations <- {
        layerIds match {
          case Some(ids) => getLayerLocations(ids)
          case _         => Map[UUID, (List[UUID], List[String])]().pure[ConnectionIO]
        }
      }
    } yield {
      parameters.flatMap {
        case Parameters(id, ProjectSrc, band, nodataOverride) =>
          val key = s"${id}_${band}"
          projectLocations.get(id) match {
            case Some((_, locations)) =>
              val value = locations.map((_, band, nodataOverride))
              Some(key -> value)
            case _ => None
          }
        case Parameters(id, LayerSrc, band, nodataOverride) =>
          val key = s"${id}_${band}"
          layerLocations.get(id) match {
            case Some((_, locations)) =>
              val value = locations.map((_, band, nodataOverride))
              Some(key -> value)
            case _ => None
          }
      }.toMap
    }
    sceneLocations
  }
}
