package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.types._
import com.rasterfoundry.datamodel._

import cats.data._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
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
    "annotation_task_id",
    "description",
    "is_active",
    "session_id"
  )
  val selectF: Fragment = fr"SELECT" ++
    selectFieldsF ++ fr", classes.class_ids as annotation_label_classes FROM " ++
    Fragment.const(tableName) ++
    fr""" JOIN (
      SELECT annotation_label_id, array_agg(annotation_class_id) as class_ids
      FROM """ ++ Fragment.const(joinTableName) ++ fr"""
      GROUP BY annotation_label_id
    ) as classes ON """ ++ Fragment.const(tableName) ++ fr".id = " ++
    fr"classes.annotation_label_id"

  val whereActiveF = fr"is_active = true"

  def insertAnnotations(
      annotationProjectId: UUID,
      annotationTaskId: UUID,
      annotations: List[AnnotationLabelWithClasses.Create],
      user: User
  ): ConnectionIO[List[AnnotationLabelWithClasses]] = {
    val insertAnnotationsFragment: Fragment =
      fr"INSERT INTO" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr") VALUES"
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
        ${annotationLabel.annotationProjectId}, ${annotationLabel.annotationTaskId},
        ${annotationLabel.description}, ${annotationLabel.isActive}, ${annotationLabel.sessionId}
       )"""
    )
    val labelClassFragments: List[Fragment] =
      annotationLabelsWithClasses flatMap { label =>
        label.annotationLabelClasses.map(labelClassId =>
          fr"(${label.id}, ${labelClassId})"
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
    query
      .filter(fr"annotation_project_id = ${projectId}")
      .filter(whereActiveF)
      .list
  }

  def countByProjectsAndGroup(
      projectIds: List[UUID],
      annotationLabelClassGroupId: UUID
  ): ConnectionIO[List[LabelClassSummary]] = {
    val projectIdsF = projectIds map { projectId =>
      fr"$projectId"
    }
    val activeLabelF = fr"al.is_active = true"
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
    al.annotation_project_id in (""" ++ projectIdsF.intercalate(fr",") ++ fr""")
  AND
    alcls.annotation_label_group_id = ${annotationLabelClassGroupId}""" ++ activeLabelF ++ fr"""
  GROUP BY
    alalc.annotation_class_id,
    alcls.name
  """)
    fragment.query[LabelClassSummary].to[List]
  }

  def listWithClassesByProjectIdAndTaskId(
      projectId: UUID,
      taskId: UUID
  ): ConnectionIO[List[AnnotationLabelWithClasses.GeoJSON]] =
    query
      .filter(fr"annotation_project_id=$projectId")
      .filter(fr"annotation_task_id=$taskId")
      .filter(whereActiveF)
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
      taskStatuses: List[String],
      labelGroupsOpt: Option[List[AnnotationLabelClassGroup]] = None
  ): ConnectionIO[Option[Json]] = {
    val taskJoinF = fr"JOIN tasks on " ++ Fragment.const(
      tableName
    ) ++ fr".annotation_task_id = tasks.id"
    val taskFilterF = fr"tasks.annotation_project_id = ${annotationProjectId}"
    val labelFilterF =
      fr"annotation_labels.annotation_project_id = ${annotationProjectId}"
    val activeLabelF = fr"annotation_labels.is_active = true"
    val statusFilterFO = taskStatuses.toNel map { statuses =>
      Fragments.in(fr"tasks.status", statuses.map(TaskStatus.fromString(_)))
    }
    val fcIo = for {
      labelGroups <- OptionT.liftF(
        labelGroupsOpt.fold(
          AnnotationLabelClassGroupDao.listByProjectId(annotationProjectId)
        )({ groups =>
          groups.pure[ConnectionIO]
        })
      )
      groupedLabelClasses <- OptionT.liftF(labelGroups traverse { group =>
        AnnotationLabelClassDao
          .listAnnotationLabelClassByGroupId(group.id)
          .map((group.id, _))
      })
      labelGroupMap = labelGroups.map(g => (g.id -> g)).toMap
      classIdToGroupName =
        groupedLabelClasses
          .map { classGroups =>
            classGroups._2.map(_.id -> labelGroupMap.get(classGroups._1))
          }
          .flatten
          .toMap
          .collect {
            case (k, Some(v)) => k -> v.name
          }
      classIdToLabelName =
        groupedLabelClasses
          .map(_._2)
          .flatten
          .map(c => c.id -> c.name)
          .toMap
      annotations <- OptionT.liftF(
        (selectF ++ taskJoinF ++ Fragments
          .whereAndOpt(
            Some(taskFilterF),
            Some(labelFilterF),
            statusFilterFO,
            Some(activeLabelF)
          ))
          .query[AnnotationLabelWithClasses]
          .to[List]
      )
    } yield StacGeoJSONFeatureCollection(
      annotations.map(anno =>
        anno.toStacGeoJSONFeature(classIdToGroupName, classIdToLabelName)
      )
    ).asJson
    fcIo.value
  }

  def copyProjectAnnotations(
      childAnnotationProjectId: ChildAnnotationProjectId,
      parentAnnotationProjectId: ParentAnnotationProjectId
  ): ConnectionIO[Unit] =
    for {
      parentTask <-
        TaskDao.query
          .filter(
            fr"annotation_project_id = ${parentAnnotationProjectId.parentAnnotationProjectId}"
          )
          .select
      _ <- fr"""
      WITH source_labels_with_classes AS (
        SELECT * FROM
          (annotation_labels JOIN annotation_labels_annotation_label_classes ON
             annotation_labels.id = annotation_labels_annotation_label_classes.annotation_label_id)
        WHERE
          annotation_project_id = ${childAnnotationProjectId.childAnnotationProjectId}
          AND is_active = true
      ),
      -- is this identical to selecting from annotation labels? probably! but running everything
      -- through the join table makes me feel more optimistic about likelihood of writing
      -- correct SQL for what I'm doing
      source_labels AS (
        SELECT id, created_at, created_by, annotation_project_id, annotation_task_id,
               geometry, description
        FROM source_labels_with_classes
      ),
      label_ids_to_classes AS (
        SELECT id, uuid_generate_v4() as new_label_id, array_agg(annotation_class_id) as class_ids
        FROM source_labels_with_classes GROUP BY id
      ),
      new_labels_insert AS (
        INSERT INTO annotation_labels (
          SELECT new_label_id, created_at, created_by,
                 ${parentAnnotationProjectId.parentAnnotationProjectId} as annotation_project_id,
                 ${parentTask.id} as annotation_task_id,
                 geometry, description
          FROM source_labels join label_ids_to_classes on source_labels.id = label_ids_to_classes.id
        )
      ),
      unnested as (
        SELECT new_label_id, unnest(class_ids) as class_id FROM label_ids_to_classes
      )
      INSERT INTO annotation_labels_annotation_label_classes (
        SELECT new_label_id, parent_label_class_id
        FROM unnested JOIN label_class_history
        ON unnested.class_id = label_class_history.child_label_class_id
      )
      """.update.run
    } yield ()
}
