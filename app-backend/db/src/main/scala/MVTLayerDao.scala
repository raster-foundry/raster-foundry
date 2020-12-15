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
          annotation_project_id = ${annotationProjectId} AND
          task_type = 'LABEL'::task_type AND
          status <> 'SPLIT'
      )
    SELECT ST_AsMVT(mvtgeom.*) FROM mvtgeom;""".query[Array[Byte]]

  def getAnnotationProjectTasks(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): ConnectionIO[Option[Array[Byte]]] =
    getAnnotationProjectTasksQ(annotationProjectId, z, x, y).option

  //-- AND tasks.annotation_project_id = ${annotationProjectId}
  // JOIN tasks on annotations.task_id = tasks.id
  private[database] def getAnnotationProjectLabelsQ(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): Query0[Array[Byte]] = {
    fr"""WITH mvtgeom AS
      (
        SELECT
          ST_AsMVTGeom(
            join_table_join.geometry,
            ST_TileEnvelope(${z},${x},${y})
          ) AS geom,
          join_table_join.annotation_task_id,
          annotation_label_classes.id as label_class_id,
          annotation_label_classes.name,
          annotation_label_classes.color_hex_code
        FROM
          (annotation_labels join (select id, status from tasks) tasks on annotation_labels.annotation_task_id = tasks.id
           JOIN annotation_labels_annotation_label_classes on
           annotation_labels.id = annotation_labels_annotation_label_classes.annotation_label_id) join_table_join
          JOIN annotation_label_classes on join_table_join.annotation_class_id = annotation_label_classes.id
        WHERE
          ST_Intersects(
            join_table_join.geometry,
            ST_TileEnvelope(${z},${x},${y})
          )
          AND join_table_join.annotation_project_id = ${annotationProjectId}
          AND status <> 'SPLIT'
      )
    SELECT ST_AsMVT(mvtgeom.*) FROM mvtgeom;""".query[Array[Byte]]
  }

  def getAnnotationProjectLabels(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): ConnectionIO[Option[Array[Byte]]] =
    getAnnotationProjectLabelsQ(annotationProjectId, z, x, y).option

}
