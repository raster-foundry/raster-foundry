package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._

import cats._, cats.data._, cats.effect.IO, cats.implicits._
import geotrellis.slick.Projected
import geotrellis.vector.Geometry
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}


object AnnotationDao extends Dao[Annotation] {

  val tableName = "annotations"

  val selectF =
    fr"""
      SELECT
        id, project_id, created_at, created_by, modified_at, modified_by, owner,
        label, description, machine_generated, confidence,
        quality, geometry, annotation_group
      FROM
    """ ++ tableF

  def unsafeGetAnnotationById(annotationId: UUID, user: User): ConnectionIO[Annotation] = {
    query.filter(annotationId).select
  }

  def listAnnotationsForProject(projectId: UUID, user: User): ConnectionIO[List[Annotation]] = {
fr"""
    SELECT
    id, project_id, created_at, created_by, modified_at, modified_by, owner,
    label, description, machine_generated, confidence,
    quality, geometry, annotation_group
    FROM
"""
    (selectF ++ Fragments.whereAndOpt(fr"project_id = ${projectId}".some))
      .query[Annotation]
      .stream.compile.toList
  }

  def insertAnnotations(
    annotations: List[Annotation.Create],
    projectId: UUID,
    user: User
  ): ConnectionIO[List[Annotation]] = {
    val updateSql = "INSERT INTO " ++ tableName ++ """
        (id, project_id, created_at, created_by, modified_at, modified_by, owner,
        label, description, machine_generated, confidence,
        quality, geometry, annotation_group)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      defaultAnnotationGroup <- project.defaultAnnotationGroup match {
        case Some(group) =>
          group.pure[ConnectionIO]
        case _ => AnnotationGroupDao.createAnnotationGroup(
          projectId, AnnotationGroup.Create("Annotations", None), user
        ).map(_.id).flatMap(defaultId =>
          ProjectDao.updateProject(project.copy(defaultAnnotationGroup = Some(defaultId)), project.id, user)
            .map(_ => defaultId)
        )
      }
      insertedAnnotations <- Update[Annotation](updateSql).updateManyWithGeneratedKeys[Annotation](
        "id", "project_id", "created_at", "created_by", "modified_at", "modified_by", "owner",
        "label", "description", "machine_generated", "confidence",
        "quality", "geometry", "annotation_group"
      )(annotations map { _.toAnnotation(projectId, user, defaultAnnotationGroup) }).compile.toList
    } yield insertedAnnotations
  }

  def updateAnnotation(annotation: Annotation, id: UUID, user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"SET" ++ fr"""
        modified_at = NOW(),
        modified_by = ${user.id},
        label = ${annotation.label},
        description = ${annotation.description},
        machine_generated = ${annotation.machineGenerated},
        confidence = ${annotation.confidence},
        quality = ${annotation.quality},
        geometry = ${annotation.geometry},
        annotation_group = ${annotation.annotationGroup}
      WHERE
        id = ${annotation.id}
    """).update.run
  }

  def listProjectLabels(projectId: UUID, user: User): ConnectionIO[List[String]] = {
    (fr"SELECT DISTINCT ON (label) label FROM" ++ tableF ++ Fragments.whereAndOpt(
      Some(fr"project_id = ${projectId}")
    )).query[String].list
  }

  def deleteByAnnotationGroup(annotationGroupId: UUID): ConnectionIO[Int] =
    query.filter(fr"annotation_group = ${annotationGroupId}").delete

}
