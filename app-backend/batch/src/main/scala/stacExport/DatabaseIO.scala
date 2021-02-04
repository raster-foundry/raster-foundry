package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.database._
import com.rasterfoundry.datamodel.TaskStatus

import cats.implicits._
import doobie._
import doobie.implicits._

import java.util.UUID

object DatabaseIO {
// In order to prevent re-writing too much of the export process,
// this function still returns a Map with a single entry.
  def sceneTaskAnnotationforLayers(
      annotationProjectId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[Option[ExportData]] =
    for {
      annotationProject <- AnnotationProjectDao.getById(annotationProjectId)
      project <- annotationProject flatMap { _.projectId } flatTraverse { pid =>
        ProjectDao.getProjectById(pid)
      }
      info <- project.fold(
        getExportData(annotationProjectId, None, taskStatuses)
      )({ proj =>
        getExportData(
          annotationProjectId,
          Some(proj.defaultLayerId),
          taskStatuses
        )
      })
    } yield info

  protected def getExportData(
      annotationProjectId: UUID,
      defaultLayerId: Option[UUID],
      taskStatuses: List[String]
  ): ConnectionIO[Option[ExportData]] = {
    for {
      tileLayers <- TileLayerDao.listByProjectId(annotationProjectId)
      scenes <- defaultLayerId traverse { layerId =>
        ProjectLayerScenesDao.listLayerScenesRaw(layerId)
      }
      // We're using the unioned extent of scenes because the STAC catalog contains
      // both scenes and annotations.
      // TODO: Restrict the StacItem bounding boxes for labels
      scenesGeomExtentOption <- defaultLayerId flatTraverse { layerId =>
        ProjectLayerScenesDao.getUnionedGeomExtent(
          layerId
        )
      }
      tasks <- TaskDao.listTasksByStatus(annotationProjectId, taskStatuses)
      tasksGeomExtentOption <- TaskDao.createUnionedGeomExtent(
        annotationProjectId,
        taskStatuses
      )
      annotationsOption <- AnnotationLabelDao
        .getAnnotationJsonByTaskStatus(
          annotationProjectId,
          taskStatuses
        )
      labelItemPropsThinOption <- AnnotationProjectDao
        .getAnnotationProjectStacInfo(annotationProjectId)
    } yield {
      (
        annotationsOption,
        labelItemPropsThinOption,
        tasksGeomExtentOption
      ) match {
        case (
            Some(annotations),
            Some(labelItemPropsThin),
            Some(tasksGeomExtent)
            ) => {
          Some(
            ExportData(
              scenes getOrElse Nil,
              scenesGeomExtentOption,
              tasks,
              tasksGeomExtent,
              annotations,
              labelItemPropsThin,
              taskStatuses map { TaskStatus.fromString },
              defaultLayerId,
              tileLayers
            )
          )
        }
        case _ => None
      }
    }
  }
}
