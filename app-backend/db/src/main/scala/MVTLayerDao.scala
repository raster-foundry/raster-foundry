package com.rasterfoundry.database

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

/** Container for methods required to get byte arrays of MVT layers from the db
  *
  * Claims to be a Dao, but doesn't extend Dao[Option[Array[Byte]]] because we can't provide
  * sensible values for some of the required Dao fields, e.g., what should the `selectF` be?
  * Or fieldnames? Or table name?
  */
object MVTLayerDao {
  private[database] def getAnnotationProjectTasksQ(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): Query0[Array[Byte]] =
    fr"""WITH mvtgeom AS
      (
        SELECT
          ST_AsMVTGeom(
            geometry,
            ST_TileEnvelope(${z},${x},${y})
          ) AS geom,
          *
        FROM tasks
        WHERE
          ST_Intersects(
            geometry,
            ST_TileEnvelope(${z},${x},${y})
          ) AND
          annotation_project_id = ${annotationProjectId}
      )
    SELECT ST_AsMVT(mvtgeom.*) FROM mvtgeom;""".query[Array[Byte]]

  def getAnnotationProjectTasks(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): ConnectionIO[Option[Array[Byte]]] =
    getAnnotationProjectTasksQ(annotationProjectId, z, x, y).option

  private[database] def getAnnotationProjectLabelsQ(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): Query0[Array[Byte]] =
    fr"""WITH mvtgeom AS
      (
        SELECT
          ST_AsMVTGeom(
            annotations.geometry,
            ST_TileEnvelope(${z},${x},${y})
          ) AS geom,
          annotations.id,
          annotations.project_id,
          annotations.created_at,
          annotations.created_by,
          annotations.modified_at,
          annotations.owner,
          annotations.label,
          annotations.description,
          annotations.machine_generated,
          annotations.confidence,
          annotations.quality,
          annotations.annotation_group,
          annotations.labeled_by,
          annotations.verified_by,
          annotations.project_layer_id,
          annotations.task_id
        FROM annotations JOIN tasks on annotations.task_id = tasks.id
        WHERE
          ST_Intersects(
            annotations.geometry,
            ST_TileEnvelope(${z},${x},${y})
          ) AND
          tasks.annotation_project_id = ${annotationProjectId}
      )
    SELECT ST_AsMVT(mvtgeom.*) FROM mvtgeom;""".query[Array[Byte]]

  def getAnnotationProjectLabels(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): ConnectionIO[Option[Array[Byte]]] =
    getAnnotationProjectLabelsQ(annotationProjectId, z, x, y).option

}
