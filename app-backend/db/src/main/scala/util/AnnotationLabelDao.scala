package com.rasterfoundry.database

import java.util.UUID
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import com.rasterfoundry.datamodel._
import com.rasterfoundry.database.Implicits._

object AnnotationLabelDao extends Dao[AnnotationLabelWithClasses] {
  val tableName = "annotation_labels"
  val joinTableName = "annotation_labels_annotation_label_classes"
  val selectF: Fragment = fr"""
  SELECT
    id, created_at, created_by, geometry, annotation_project_id, annotation_task_id,
    classes.class_ids as annotation_label_classes,
  FROM ${tableName} JOIN (
    SELECT annotation_label_id, array_agg(annotation_class_id) as class_ids
    FROM ${joinTableName}
    GROUP BY annotation_label_id
  ) as classes ON ${tableName}.id = ${joinTableName}.annotation_label_id
  """
  def insertAnnotations(
      annotations: List[AnnotationLabelWithClasses.Create],
      user: User
  ): ConnectionIO[List[AnnotationLabelWithClasses]] = {
    val insertAnnotationsFragment: Fragment =
      fr"INSERT INTO" ++ tableF ++ fr"""(
      id, created_at, created_by, geometry, annotation_project_id, annotation_task_id
    ) VALUES
    """
    val insertClassesFragment: Fragment =
      fr"INSERT INTO" ++ Fragment.const(joinTableName) ++ fr"""(
      annotation_label_id, annotation_label_class_id
    ) VALUES
    """
    val annotationLabelsWithClasses =
      annotations.map(_.toAnnotationLabelWithClasses(user))
    val annotationFragments: List[Fragment] = annotationLabelsWithClasses.map(
      (annotationLabel: AnnotationLabelWithClasses) => fr"""(
        ${annotationLabel.id}, ${annotationLabel.createdAt},
        ${annotationLabel.createdBy}, ${annotationLabel.geometry},
        ${annotationLabel.annotationProjectId}, ${annotationLabel.annotationTaskId}
       )"""
    )
    val labelClassFragments: List[Fragment] =
      annotationLabelsWithClasses.flatMap(
        (annotationLabel: AnnotationLabelWithClasses) =>
          annotationLabel.annotationLabelClasses
            .map(
              labelClassId => fr"${annotationLabel.id}, ${labelClassId}"
            )
            .toList
      )
    for {
      insertedAnnotations <- annotationFragments.toNel
        .map(
          fragments =>
            (insertAnnotationsFragment ++ fragments.intercalate(fr",")).update
              .withGeneratedKeys[AnnotationLabel](
                "id",
                "created_at",
                "created_by",
                "geometry",
                "annotation_project_id",
                "annotation_task_id"
              )
              .compile
              .toList
        )
        .getOrElse(List[AnnotationLabel]().pure[ConnectionIO])
      insertedAnnotationClasses <- labelClassFragments.toNel
        .map(
          fragments =>
            (insertClassesFragment ++ fragments.intercalate(fr",")).update
              .withGeneratedKeys[(UUID, UUID)](
                "annotation_label_id",
                "annotation_label_class_id"
              )
              .compile
              .toList
        )
        .getOrElse(List[(UUID, UUID)]().pure[ConnectionIO])
      labelsToClasses = insertedAnnotationClasses.groupBy(_._1)
      insertedAnnotationsWithClasses = insertedAnnotations.map(
        anno =>
          AnnotationLabelWithClasses(
            anno.id,
            anno.createdAt,
            anno.createdBy,
            anno.geometry,
            anno.annotationProjectId,
            anno.annotationTaskId,
            labelsToClasses
              .getOrElse(anno.id, Seq[(UUID, UUID)]())
              .map(_._2)
              .toList
        )
      )
    } yield insertedAnnotationsWithClasses
  }

  def listProjectLabels(
      projectId: UUID
  ): ConnectionIO[List[AnnotationLabelWithClasses]] = {
    query.filter(fr"annotation_project_id = ${projectId}").list
  }

  def countByProjectAndGroup(
      projectId: UUID,
      annotationLabelClassGroupId: UUID
  ): ConnectionIO[List[AnnotationProject.LabelClassSummary]] = (fr"""
  SELECT 
    alalc.annotation_class_id AS label_class_id, 
    alcls.name AS label_class_name,
    count(al.id) AS count
  FROM annotation_labels AS al
  JOIN annotation_labels_annotation_label_classes AS alalc
  ON alalc.annotation_label_id = al.id
  JOIN annotation_label_classes AS alcls
  ON alcls.id = alalc.annotation_class_id
  WHERE 
    al.annotation_project_id = ${projectId}
    AND
    alcls.annotation_label_group_id = ${annotationLabelClassGroupId}
  GROUP BY
    alalc.annotation_class_id,
    alcls.name
  """).query[AnnotationProject.LabelClassSummary].to[List]
}
