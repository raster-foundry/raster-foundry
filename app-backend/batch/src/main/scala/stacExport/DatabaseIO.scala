package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._
import java.util.UUID
import doobie.implicits._
import doobie._
import cats.implicits._

object DatabaseIO {
  def sceneTaskAnnotationforLayers(
      layerDefinitions: List[StacExport.LayerDefinition],
      taskStatuses: List[String]
  ): ConnectionIO[Map[UUID, ExportData]] = {

    (layerDefinitions traverse {
      case StacExport.LayerDefinition(projectId, layerId) =>
        for {
          projectTypeO <- ProjectDao.getAnnotationProjectType(projectId)
          infoOption <- projectTypeO match {
            case Some(projectType) =>
              createLayerInfoMap(projectId, layerId, taskStatuses, projectType)
            case _ => Option.empty.pure[ConnectionIO]
          }
        } yield {
          infoOption match {
            case Some(info) => Some((layerId, info))
            case _          => None
          }
        }
    }) map {
      _.flatten.toMap
    }
  }

  protected def createLayerInfoMap(
      projectId: UUID,
      layerId: UUID,
      taskStatuses: List[String],
      projectType: MLProjectType
  ): ConnectionIO[Option[ExportData]] = {
    for {
      scenes <- ProjectLayerScenesDao.listLayerScenesRaw(layerId)
      scenesGeomExtentOption <- ProjectLayerScenesDao.getUnionedGeomExtent(
        layerId
      )
      tasks <- TaskDao.listLayerTasksByStatus(projectId, layerId, taskStatuses)
      tasksGeomExtentOption <- TaskDao.createUnionedGeomExtentOld(
        projectId,
        layerId,
        taskStatuses
      )
      annotationsOption <- AnnotationDao.getLayerAnnotationJsonByTaskStatus(
        projectId,
        layerId,
        taskStatuses,
        projectType
      )
      labelItemPropsThinOption <- ProjectDao.getAnnotationProjectStacInfo(
        projectId
      )
    } yield {
      (
        annotationsOption,
        labelItemPropsThinOption,
        tasksGeomExtentOption,
        scenesGeomExtentOption
      ) match {
        case (
            Some(annotations),
            Some(labelItemPropsThin),
            Some(tasksGeomExtent),
            Some(scenesGeomExtent)
            ) => {
          Some(
            ExportData(
              scenes,
              scenesGeomExtent,
              tasks,
              tasksGeomExtent,
              annotations,
              labelItemPropsThin
            )
          )
        }
        case _ => None
      }
    }
  }
}
