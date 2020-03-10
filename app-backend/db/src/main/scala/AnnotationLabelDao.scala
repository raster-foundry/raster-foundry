package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.data._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import io.circe._
import io.circe.syntax._

import java.util.UUID

object AnnotationLabelDao extends Dao[AnnotationLabelWithClasses] {
  val tableName = "annotation_labels"
  val joinTableName = "annotation_labels_annotation_label_classes"
  override val fieldNames = List(
    "id",
    "created_at",
    "created_by",
    "geometry",
    "annotation_project_id",
    "annotation_task_id"
  )
  val selectF: Fragment = fr"SELECT" ++
    fieldsF ++ fr", classes.class_ids as annotation_label_classes FROM " ++
    Fragment.const(tableName) ++
    fr""" JOIN (
      SELECT annotation_label_id, array_agg(annotation_class_id) as class_ids
      FROM """ ++ Fragment.const(joinTableName) ++ fr"""
      GROUP BY annotation_label_id
    ) as classes ON """ ++ Fragment.const(tableName) ++ fr".id = " ++
    fr"classes.annotation_label_id"

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

  def listWithClassesByProjectIdAndTaskId(
      projectId: UUID,
      taskId: UUID
  ): ConnectionIO[List[AnnotationLabelWithClasses.GeoJSON]] =
    query
      .filter(fr"annotation_project_id=$projectId")
      .filter(fr"annotation_task_id=$taskId")
      .list
      .map { listed =>
        listed.map(_.toGeoJSONFeature)
      }

  def deleteByProjectIdAndTaskId(
      projectId: UUID,
      taskId: UUID
  ): ConnectionIO[Int] =
    (fr"DELETE FROM" ++ tableF ++ Fragments.whereAndOpt(
      Some(fr"annotation_project_id=$projectId"),
      Some(fr"annotation_task_id=$taskId")
    )).update.run

  def getAnnotationJsonByTaskStatus(
      annotationProjectId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[Option[Json]] = {
    val taskJoinF = fr"JOIN tasks on " ++ Fragment.const(tableName) ++ fr".annotation_task_id = tasks.id"
    val taskFilterF = fr"tasks.annotation_project_id = ${annotationProjectId}"
    val labelFilterF =
      fr"annotation_labels.annotation_project_id = ${annotationProjectId}"
    val statusFilterFO = taskStatuses.toNel map { statuses =>
      Fragments.in(fr"tasks.status", statuses.map(TaskStatus.fromString(_)))
    }
    val fcIo = for {
      labelGroups <- OptionT.liftF(
        AnnotationLabelClassGroupDao.listByProjectId(annotationProjectId)
      )
      groupedLabelClasses <- OptionT.liftF(labelGroups traverse { group =>
        AnnotationLabelClassDao
          .listAnnotationLabelClassByGroupId(group.id)
          .map((group.id, _))
      })
      labelGroupMap = labelGroups.map(g => (g.id -> g)).toMap
      classIdToGroupName = groupedLabelClasses
        .map { classGroups =>
          classGroups._2.map(_.id -> labelGroupMap.get(classGroups._1))
        }
        .flatten
        .toMap
        .collect {
          case (k, Some(v)) => k -> v.name
        }
      classIdToLabelName = groupedLabelClasses
        .map(_._2)
        .flatten
        .map(c => c.id -> c.name)
        .toMap
      annotations <- OptionT.liftF(
        (selectF ++ taskJoinF ++ Fragments
          .whereAndOpt(Some(taskFilterF), Some(labelFilterF), statusFilterFO))
          .query[AnnotationLabelWithClasses]
          .to[List]
      )
    } yield
      StacGeoJSONFeatureCollection(
        annotations.map(
          anno =>
            anno.toStacGeoJSONFeature(classIdToGroupName, classIdToLabelName)
        )
      ).asJson
    fcIo.value
  }
}
