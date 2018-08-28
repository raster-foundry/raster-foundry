package com.azavea.rf.database

import java.util.UUID

import cats.implicits._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

object AnnotationDao extends Dao[Annotation] {

  val tableName = "annotations"

  val selectF: Fragment =
    fr"""
      SELECT
        id, project_id, created_at, created_by, modified_at, modified_by, owner,
        label, description, machine_generated, confidence,
        quality, geometry, annotation_group, labeled_by, verified_by
      FROM
    """ ++ tableF

  def unsafeGetAnnotationById(annotationId: UUID): ConnectionIO[Annotation] = {
    query.filter(annotationId).select
  }

  def listAnnotationsForProject(
      projectId: UUID): ConnectionIO[List[Annotation]] = {
    fr"""
    SELECT
    id, project_id, created_at, created_by, modified_at, modified_by, owner,
    label, description, machine_generated, confidence,
    quality, geometry, annotation_group, labeled_by, verified_by
    FROM
"""
    (selectF ++ Fragments.whereAndOpt(fr"project_id = ${projectId}".some))
      .query[Annotation]
      .stream
      .compile
      .toList
  }

  def insertAnnotations(
      annotations: List[Annotation.Create],
      projectId: UUID,
      user: User
  ): ConnectionIO[List[Annotation]] = {
    val updateSql = "INSERT INTO " ++ tableName ++ """
        (id, project_id, created_at, created_by, modified_at, modified_by, owner,
        label, description, machine_generated, confidence,
        quality, geometry, annotation_group, labeled_by, verified_by)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      defaultAnnotationGroup <- project.defaultAnnotationGroup match {
        case Some(group) =>
          group.pure[ConnectionIO]
        case _ =>
          AnnotationGroupDao
            .createAnnotationGroup(
              projectId,
              AnnotationGroup.Create("Annotations", None),
              user
            )
            .map(_.id)
            .flatMap(
              defaultId =>
                ProjectDao
                  .updateProject(
                    project.copy(defaultAnnotationGroup = Some(defaultId)),
                    project.id,
                    user)
                  .map(_ => defaultId))
      }
      insertedAnnotations <- Update[Annotation](updateSql)
        .updateManyWithGeneratedKeys[Annotation](
          "id",
          "project_id",
          "created_at",
          "created_by",
          "modified_at",
          "modified_by",
          "owner",
          "label",
          "description",
          "machine_generated",
          "confidence",
          "quality",
          "geometry",
          "annotation_group",
          "labeled_by",
          "verified_by"
        )(annotations map {
          _.toAnnotation(projectId, user, defaultAnnotationGroup)
        })
        .compile
        .toList
    } yield insertedAnnotations
  }

  def updateAnnotation(annotation: Annotation,
                       user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"SET" ++ fr"""
        modified_at = NOW(),
        modified_by = ${user.id},
        label = ${annotation.label},
        description = ${annotation.description},
        machine_generated = ${annotation.machineGenerated},
        confidence = ${annotation.confidence},
        quality = ${annotation.quality},
        geometry = ${annotation.geometry},
        annotation_group = ${annotation.annotationGroup},
        labeled_by = ${annotation.labeledBy},
        verified_by = ${annotation.verifiedBy}
      WHERE
        id = ${annotation.id}
    """).update.run
  }

  def listProjectLabels(projectId: UUID): ConnectionIO[List[String]] = {
    (fr"SELECT DISTINCT ON (label) label FROM" ++ tableF ++ Fragments
      .whereAndOpt(
        Some(fr"project_id = ${projectId}")
      )).query[String].to[List]
  }

  def deleteByAnnotationGroup(annotationGroupId: UUID): ConnectionIO[Int] =
    query.filter(fr"annotation_group = ${annotationGroupId}").delete

}
