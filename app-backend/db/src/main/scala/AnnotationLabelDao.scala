package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object AnnotationLabelDao extends Dao[AnnotationLabelWithClasses] {
  val tableName = "annotation_labels"
  val joinTableName = "annotation_labels_annotation_label_classes"

  val selectF: Fragment = fr"""
  SELECT
    id, created_at, created_by, geometry, annotation_project_id, annotation_task_id,
    classes.class_ids
  FROM """ ++ tableF ++ fr"""JOIN (
    SELECT annotation_label_id, array_agg(annotation_class_id) as class_ids
    FROM """ ++ Fragment.const(joinTableName) ++ fr"""
    GROUP BY annotation_label_id
  ) as classes ON """ ++ Fragment.const(
    s"${tableName}.id = classes.annotation_label_id"
  )

  def insertAnnotations(
      annotationProjectId: UUID,
      annotationTaskId: UUID,
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
      annotation_label_id, annotation_class_id
    ) VALUES
    """

    val annotationLabelsWithClasses =
      annotations.map(
        _.toAnnotationLabelWithClasses(
          annotationProjectId,
          annotationTaskId,
          user
        )
      )

    val annotationFragments: List[Fragment] = annotationLabelsWithClasses.map(
      (annotationLabel: AnnotationLabelWithClasses) => fr"""(
        ${annotationLabel.id}, ${annotationLabel.createdAt},
        ${annotationLabel.createdBy}, ${annotationLabel.geometry},
        ${annotationLabel.annotationProjectId}, ${annotationLabel.annotationTaskId}
       )"""
    )

    val labelClassFragments: List[Fragment] =
      annotationLabelsWithClasses flatMap { label =>
        label.annotationLabelClasses.map(
          labelClassId => fr"(${label.id}, ${labelClassId})"
        )
      }

    for {
      insertedAnnotationIds <- annotationFragments.toNel traverse { fragments =>
        (insertAnnotationsFragment ++ fragments.intercalate(fr",")).update
          .withGeneratedKeys[UUID](
            "id"
          )
          .compile
          .toList
      }
      _ <- labelClassFragments.toNel map { fragments =>
        (insertClassesFragment ++ fragments.intercalate(fr",")).update.run
      } getOrElse { ().pure[ConnectionIO] }
      recent <- insertedAnnotationIds flatMap { _.toNel } traverse {
        insertedIds =>
          query.filter(Fragments.in(fr"id", insertedIds)).list
      }
    } yield { recent getOrElse Nil }
  }

  def listProjectLabels(
      projectId: UUID
  ): ConnectionIO[List[AnnotationLabelWithClasses]] = {
    query.filter(fr"annotation_project_id = ${projectId}").list
  }

  def countByProjectAndGroup(
      projectId: UUID,
      annotationLabelClassGroupId: UUID
  ): ConnectionIO[List[AnnotationProject.LabelClassSummary]] = {
    val fragment = (fr"""
  SELECT
    alalc.annotation_class_id AS label_class_id,
    alcls.name AS label_class_name,
    count(al.id) AS count
  FROM (
    annotation_labels AS al
    JOIN
      annotation_labels_annotation_label_classes AS alalc
      ON alalc.annotation_label_id = al.id
    JOIN annotation_label_classes AS alcls
      ON alcls.id = alalc.annotation_class_id
  )
  WHERE
    al.annotation_project_id = ${projectId}
  AND
    alcls.annotation_label_group_id = ${annotationLabelClassGroupId}
  GROUP BY
    alalc.annotation_class_id,
    alcls.name
  """)
    fragment.query[AnnotationProject.LabelClassSummary].to[List]
  }
}
