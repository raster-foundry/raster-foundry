package com.rasterfoundry.database

import java.util.UUID

import cats.implicits._
import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.database.Implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest

object AnnotationDao extends Dao[Annotation] {

  val tableName = "annotations"

  val selectF: Fragment =
    fr"""
      SELECT
        id, project_id, created_at, created_by, modified_at, modified_by, owner,
        label, description, machine_generated, confidence,
        quality, geometry, annotation_group, labeled_by, verified_by,
        project_layer_id
      FROM
    """ ++ tableF

  def unsafeGetAnnotationById(annotationId: UUID): ConnectionIO[Annotation] =
    query.filter(annotationId).select

  def getAnnotationById(projectId: UUID,
                        annotationId: UUID): ConnectionIO[Option[Annotation]] =
    query
      .filter(fr"project_id = ${projectId}")
      .filter(annotationId)
      .selectOption

  def listAnnotationsForProject(
      projectId: UUID): ConnectionIO[List[Annotation]] = {
    (selectF ++ Fragments.whereAndOpt(fr"project_id = ${projectId}".some))
      .query[Annotation]
      .stream
      .compile
      .toList
  }

  def listForExport(projectF: Fragment,
                    layerF: Fragment): ConnectionIO[List[Annotation]] =
    AnnotationDao.query.filter(projectF).filter(layerF).list

  // list default project layer annotations if exportAll is None or Some(false)
  // list all annotations if exportAll is true
  def listForProjectExport(
      projectId: UUID,
      annotationExportQP: AnnotationExportQueryParameters
  ): ConnectionIO[List[Annotation]] =
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      annotations <- listForExport(
        fr"project_id=$projectId",
        annotationExportQP.exportAll match {
          case Some(true) => fr""
          case _          => fr"project_layer_id=${project.defaultLayerId}"
        }
      )
    } yield { annotations }

  def listForLayerExport(projectId: UUID,
                         layerId: UUID): ConnectionIO[List[Annotation]] =
    listForExport(fr"project_id=$projectId", fr"project_layer_id=$layerId")

  // look for default project layer
  // if projectLayerIdO is not provided as the last param
  def listByLayer(
      projectId: UUID,
      page: PageRequest,
      queryParams: AnnotationQueryParameters,
      projectLayerIdO: Option[UUID] = None
  ): ConnectionIO[PaginatedResponse[Annotation]] =
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = ProjectDao.getProjectLayerId(projectLayerIdO, project)
      annotations <- AnnotationDao.query
        .filter(fr"project_id=$projectId")
        .filter(fr"project_layer_id=${projectLayerId}")
        .filter(queryParams)
        .page(page)
    } yield { annotations }

  // look for the default project layer
  // if projectLayerIdO is not provided as the last param
  def insertAnnotations(
      annotations: List[Annotation.Create],
      projectId: UUID,
      user: User,
      projectLayerIdO: Option[UUID] = None
  ): ConnectionIO[List[Annotation]] = {
    val updateSql = "INSERT INTO " ++ tableName ++ """
        (id, project_id, created_at, created_by, modified_at, modified_by, owner,
        label, description, machine_generated, confidence,
        quality, geometry, annotation_group, labeled_by, verified_by,
        project_layer_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = ProjectDao.getProjectLayerId(projectLayerIdO, project)
      defaultAnnotationGroup <- project.defaultAnnotationGroup match {
        case Some(group) =>
          group.pure[ConnectionIO]
        case _ =>
          AnnotationGroupDao
            .createAnnotationGroup(
              projectId,
              AnnotationGroup.Create("Annotations", None),
              user,
              Some(projectLayerId)
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
          "verified_by",
          "project_layer_id"
        )(annotations map {
          _.toAnnotation(
            projectId,
            user,
            defaultAnnotationGroup,
            projectLayerId
          )
        })
        .compile
        .toList
    } yield insertedAnnotations
  }

  def updateAnnotation(projectId: UUID,
                       annotation: Annotation,
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
        verified_by = ${annotation.verifiedBy},
        project_layer_id = ${annotation.projectLayerId}
      WHERE
        id = ${annotation.id} AND project_id = ${projectId}
    """).update.run
  }

  def listProjectLabels(
      projectId: UUID,
      projectLayerIdO: Option[UUID] = None): ConnectionIO[List[String]] = {
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = ProjectDao.getProjectLayerId(projectLayerIdO, project)
      labelList <- (fr"SELECT DISTINCT ON (label) label FROM" ++ tableF ++ Fragments
        .whereAndOpt(
          Some(fr"project_id = ${projectId}"),
          Some(fr"project_layer_id = ${projectLayerId}")
        )).query[String].to[List]
    } yield { labelList }
  }

  def deleteByAnnotationGroup(annotationGroupId: UUID): ConnectionIO[Int] =
    query.filter(fr"annotation_group = ${annotationGroupId}").delete

  // look for the default project layer
  // if projectLayerIdO is not provided as the last param
  def deleteByProjectLayer(
      projectId: UUID,
      projectLayerIdO: Option[UUID] = None): ConnectionIO[Int] =
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = ProjectDao.getProjectLayerId(projectLayerIdO, project)
      rowsDeleted <- AnnotationDao.query
        .filter(fr"project_id = ${projectId}")
        .filter(fr"project_layer_id = ${projectLayerId}")
        .delete
    } yield { rowsDeleted }

  def deleteById(projectId: UUID, annotationId: UUID): ConnectionIO[Int] =
    query.filter(fr"project_id = ${projectId}").filter(annotationId).delete
}
