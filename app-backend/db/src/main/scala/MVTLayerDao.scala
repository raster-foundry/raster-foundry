package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._

import cats.Monoid
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.vector.MultiPolygon
import geotrellis.vector.Polygon
import geotrellis.vector.{Geometry, Projected, ProjectedExtent}
import geotrellis.vectortile._

import java.util.UUID

/** Container for methods required to get byte arrays of MVT layers from the db
  *
  * Claims to be a Dao, but doesn't extend Dao[Option[Array[Byte]]] because we can't provide
  * sensible values for some of the required Dao fields, e.g., what should the `selectF` be?
  * Or fieldnames? Or table name?
  */
object MVTLayerDao {

  final case class LabelTileGeometry(
      geom: Projected[Geometry],
      envelope: ProjectedExtent,
      taskId: UUID,
      labelClassId: UUID,
      className: String,
      colorHexCode: String
  ) {
    def vtValues: Map[String, Value] =
      Map(
        "annotation_task_id" -> VString(taskId.toString),
        "label_class_id" -> VString(labelClassId.toString),
        "name" -> VString(className),
        "color_hex_code" -> VString(colorHexCode)
      )
  }

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
  ): ConnectionIO[Array[Byte]] =
    getAnnotationProjectTasksQ(annotationProjectId, z, x, y).unique

  //-- AND tasks.annotation_project_id = ${annotationProjectId}
  // JOIN tasks on annotations.task_id = tasks.id
  private[database] def getAnnotationProjectLabelsQ(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): Query0[LabelTileGeometry] = {
    fr"""
        SELECT
          annotation_labels.geometry,
          ST_TileEnvelope(${z}, ${x}, ${y}) as envelope,
          annotation_labels.annotation_task_id,
          annotation_label_classes.id as label_class_id,
          annotation_label_classes.name,
          annotation_label_classes.color_hex_code
        FROM
          (annotation_labels join tasks on annotation_labels.annotation_task_id = tasks.id
           JOIN annotation_labels_annotation_label_classes on
           annotation_labels.id = annotation_labels_annotation_label_classes.annotation_label_id)
          JOIN annotation_label_classes on annotation_labels_annotation_label_classes.annotation_class_id = annotation_label_classes.id
        WHERE
          ST_Intersects(
            annotation_labels.geometry,
            ST_TileEnvelope(${z}, ${x}, ${y})
          )
          AND annotation_labels.annotation_project_id = ${annotationProjectId}
          AND tasks.status <> 'SPLIT'
      """.query[LabelTileGeometry]
  }

  /** We know the `foldMap` will produce an inhabited stream, since
    * that's what the Monoid instance is for, so it's safe to compile it
    * to a list and take the head.
    */
  @SuppressWarnings(Array("TraversableHead"))
  def getAnnotationProjectLabels(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): ConnectionIO[Array[Byte]] = {
    implicit val monoidStrictLayer = getMonoidStrictLayer(z, x, y)
    getAnnotationProjectLabelsQ(annotationProjectId, z, x, y).stream
      .foldMap({ labelTileGeom =>
        labelTileGeom.geom.geom match {
          case g: MultiPolygon =>
            Monoid[StrictLayer].empty.copy(
              multiPolygons = List(
                MVTFeature(
                  None,
                  g,
                  labelTileGeom.vtValues
                )
              )
            )
          case g: Polygon =>
            Monoid[StrictLayer].empty.copy(
              polygons = List(
                MVTFeature(
                  None,
                  g,
                  labelTileGeom.vtValues
                )
              )
            )
          case _ => Monoid[StrictLayer].empty
        }
      })
      .compile
      .toList map { layers =>
      val first = layers.head
      VectorTile(Map("default" -> first), first.tileExtent).toBytes
    }
  }

  // this is not a lawful monoid, because it doesn't satisfy left and right identity
  // laws. _however_, for our purposes, the only layers in the universe
  // are those of the correct size that are named default for MVT spec version 2.
  // it's possible that in the future we'll want to start naming layers, but until
  // then this monoid is lawful for the universe of StrictLayers that Raster Foundry
  // can produce
  private def getMonoidStrictLayer(
      z: Int,
      x: Int,
      y: Int
  ): Monoid[StrictLayer] =
    new Monoid[StrictLayer] {
      def combine(x: StrictLayer, y: StrictLayer): StrictLayer =
        StrictLayer(
          x.name,
          x.tileWidth,
          x.version,
          x.tileExtent.combine(y.tileExtent),
          x.points ++ y.points,
          x.multiPoints ++ y.multiPoints,
          x.lines ++ y.lines,
          x.multiLines ++ y.multiLines,
          x.polygons ++ y.polygons,
          x.multiPolygons ++ y.multiPolygons
        )

      def empty: StrictLayer =
        StrictLayer(
          "default",
          256,
          2,
          tiling.tmsLevels(z).mapTransform.keyToExtent(x, y),
          Nil,
          Nil,
          Nil,
          Nil,
          Nil,
          Nil
        )
    }

}
