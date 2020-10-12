package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.database._

import cats.data._
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
  ): ConnectionIO[Map[UUID, ExportData]] = {
    val dbIO = for {
      annotationProject <- OptionT(
        AnnotationProjectDao.getById(annotationProjectId)
      )
      project <- OptionT {
        annotationProject.projectId traverse { pid =>
          ProjectDao.getProjectById(pid)
        } map { _.flatten }
      }
      info <- OptionT {
        // AnnotationProjects use the default layer of an RF project
        createLayerInfoMap(
          annotationProjectId,
          project.defaultLayerId,
          taskStatuses
        )
      }
    } yield (project.defaultLayerId, info)
    dbIO.value.map { _.toMap }
  }

  protected def createLayerInfoMap(
      annotationProjectId: UUID,
      defaultLayerId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[Option[ExportData]] = {
    for {
      scenes <- ProjectLayerScenesDao.listLayerScenesRaw(defaultLayerId)
      // We're using the unioned extent of scenes because the STAC catalog contains
      // both scenes and annotations.
      // TODO: Restrict the StacItem bounding boxes for labels
      scenesGeomExtentOption <- ProjectLayerScenesDao.getUnionedGeomExtent(
        defaultLayerId
      )
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
