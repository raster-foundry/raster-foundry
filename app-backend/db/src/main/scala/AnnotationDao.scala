package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.filter.Filters
import com.rasterfoundry.database.util.Page
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.{Order, PageRequest}

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._

import java.util.UUID

object AnnotationDao extends Dao[Annotation] {

  val tableName = "annotations"

  val selectF: Fragment =
    fr"""
      SELECT
        id, project_id, created_at, created_by, modified_at, owner,
        label, description, machine_generated, confidence,
        quality, geometry, annotation_group, labeled_by, verified_by,
        project_layer_id, task_id
      FROM
    """ ++ tableF

  def unsafeGetAnnotationById(annotationId: UUID): ConnectionIO[Annotation] =
    query.filter(annotationId).select

  def getAnnotationById(
      projectId: UUID,
      annotationId: UUID
  ): ConnectionIO[Option[AnnotationWithOwnerInfo]] =
    for {
      annotationO <-
        query
          .filter(fr"project_id = ${projectId}")
          .filter(annotationId)
          .selectOption
      ownerO <- annotationO match {
        case Some(annotation) => UserDao.getUserById(annotation.owner)
        case None             => None.pure[ConnectionIO]
      }
    } yield {
      (annotationO, ownerO) match {
        case (Some(annotation), Some((owner, _))) =>
          Some(
            AnnotationWithOwnerInfo(
              annotation.id,
              annotation.projectId,
              annotation.createdAt,
              annotation.createdBy,
              annotation.modifiedAt,
              owner.id,
              annotation.label,
              annotation.description,
              annotation.machineGenerated,
              annotation.confidence,
              annotation.quality,
              annotation.geometry,
              annotation.annotationGroup,
              annotation.labeledBy,
              annotation.verifiedBy,
              annotation.projectLayerId,
              annotation.taskId,
              owner.name,
              owner.profileImageUri
            )
          )
        case _ => None
      }
    }

  def listAnnotationsForProject(
      projectId: UUID
  ): ConnectionIO[List[Annotation]] = {
    (selectF ++ Fragments.whereAndOpt(fr"project_id = ${projectId}".some))
      .query[Annotation]
      .stream
      .compile
      .toList
  }

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
      annotations <-
        AnnotationDao.query
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
    val insertFragment: Fragment = fr"INSERT INTO" ++ tableF ++ fr"""(
      id, project_id, created_at, created_by, modified_at, owner,
      label, description, machine_generated, confidence,
      quality, geometry, annotation_group, labeled_by, verified_by,
      project_layer_id, task_id
    ) VALUES"""
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
            .flatMap(defaultId =>
              ProjectDao
                .updateProject(
                  project.copy(defaultAnnotationGroup = Some(defaultId)),
                  project.id
                )
                .map(_ => defaultId)
            )
      }
      annotationFragments: List[Fragment] =
        annotations.map((annotationCreate: Annotation.Create) => {
          val annotation: Annotation = annotationCreate.toAnnotation(
            projectId,
            user,
            defaultAnnotationGroup,
            projectLayerId
          )
          fr"""(
          ${annotation.id}, ${annotation.projectId}, ${annotation.createdAt}, ${annotation.createdBy},
          ${annotation.modifiedAt}, ${annotation.owner}, ${annotation.label},
          ${annotation.description}, ${annotation.machineGenerated}, ${annotation.confidence}, ${annotation.quality},
          ${annotation.geometry}, ${annotation.annotationGroup}, ${annotation.labeledBy}, ${annotation.verifiedBy},
          ${annotation.projectLayerId}, ${annotation.taskId}
        )"""
        })
      insertedAnnotations <-
        annotationFragments.toNel
          .map(fragments =>
            (insertFragment ++ fragments.intercalate(fr",")).update
              .withGeneratedKeys[Annotation](
                "id",
                "project_id",
                "created_at",
                "created_by",
                "modified_at",
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
                "project_layer_id",
                "task_id"
              )
              .compile
              .toList
          )
          .getOrElse(List[Annotation]().pure[ConnectionIO])
    } yield insertedAnnotations
  }

  def updateAnnotation(
      projectId: UUID,
      annotation: Annotation
  ): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"SET" ++ fr"""
        modified_at = NOW(),
        label = ${annotation.label},
        description = ${annotation.description},
        machine_generated = ${annotation.machineGenerated},
        confidence = ${annotation.confidence},
        quality = ${annotation.quality},
        geometry = ${annotation.geometry},
        annotation_group = ${annotation.annotationGroup},
        labeled_by = ${annotation.labeledBy},
        verified_by = ${annotation.verifiedBy},
        project_layer_id = ${annotation.projectLayerId},
        task_id = ${annotation.taskId}
      WHERE
        id = ${annotation.id} AND project_id = ${projectId}
    """).update.run
  }

  def listProjectLabels(
      projectId: UUID,
      projectLayerIdO: Option[UUID] = None
  ): ConnectionIO[List[String]] = {
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = ProjectDao.getProjectLayerId(projectLayerIdO, project)
      labelList <-
        (fr"SELECT DISTINCT ON (label) label FROM" ++ tableF ++ Fragments
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
      projectLayerIdO: Option[UUID] = None
  ): ConnectionIO[Int] =
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = ProjectDao.getProjectLayerId(projectLayerIdO, project)
      rowsDeleted <-
        AnnotationDao.query
          .filter(fr"project_id = ${projectId}")
          .filter(fr"project_layer_id = ${projectLayerId}")
          .delete
    } yield { rowsDeleted }

  def deleteById(projectId: UUID, annotationId: UUID): ConnectionIO[Int] =
    query.filter(fr"project_id = ${projectId}").filter(annotationId).delete

  def createAnnotateWithOwnerInfoFilters(
      projectId: UUID,
      projectLayerId: UUID,
      queryParams: AnnotationQueryParameters
  ): List[Option[Fragment]] =
    Filters.userQP(queryParams.userParams) ++
      List(
        Some(fr"a.project_id = ${projectId}"),
        Some(fr"a.project_layer_id=${projectLayerId}"),
        queryParams.label.map({ label =>
          fr"a.label = $label"
        }),
        queryParams.machineGenerated.map({ mg =>
          fr"a.machine_generated = $mg"
        }),
        queryParams.quality.map({ quality =>
          fr"a.quality = $quality"
        }),
        queryParams.annotationGroup.map({ ag =>
          fr"a.annotation_group = $ag"
        }),
        queryParams.taskId.map({ tid =>
          fr"a.task_id = $tid"
        }),
        queryParams.bboxPolygon match {
          case Some(bboxPolygons) =>
            val fragments = bboxPolygons.map(bbox =>
              fr"(_ST_Intersects(a.geometry, ${bbox}) AND a.geometry && ${bbox})"
            )
            Some(fr"(" ++ Fragments.or(fragments: _*) ++ fr")")
          case _ => None
        }
      )

  def listByLayerWithOwnerInfo(
      projectId: UUID,
      pageRequest: PageRequest,
      queryParams: AnnotationQueryParameters,
      projectLayerIdO: Option[UUID] = None
  ): ConnectionIO[PaginatedResponse[AnnotationWithOwnerInfo]] = {
    val selectF: Fragment = fr"""
      SELECT a.id, a.project_id, a.created_at, a.created_by, a.modified_at,
        a.owner, a.label, a.description, a.machine_generated,
        a.confidence, a.quality, a.geometry, a.annotation_group, a.labeled_by,
        a.verified_by, a.project_layer_id, a.task_id, u.name owner_name,
        u.profile_image_uri owner_profile_image_uri
    """

    val fromF: Fragment = fr"""
        FROM annotations a
        JOIN users u on a.owner = u.id
    """

    val countF: Fragment = fr"SELECT count(a.id)" ++ fromF

    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = ProjectDao.getProjectLayerId(projectLayerIdO, project)
      filters = createAnnotateWithOwnerInfoFilters(
        projectId,
        projectLayerId,
        queryParams
      )
      page <-
        (selectF ++ fromF ++ Fragments.whereAndOpt(filters: _*) ++ Page(
          pageRequest.copy(
            sort = pageRequest.sort ++ Map(
              "a.modified_at" -> Order.Desc,
              "a.id" -> Order.Desc
            )
          )
        )).query[AnnotationWithOwnerInfo]
          .to[List]
      count <-
        (countF ++ Fragments.whereAndOpt(filters: _*))
          .query[Long]
          .unique
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = (pageRequest.offset * pageRequest.limit) + 1 < count

      PaginatedResponse[AnnotationWithOwnerInfo](
        count,
        hasPrevious,
        hasNext,
        pageRequest.offset,
        pageRequest.limit,
        page
      )
    }
  }
}
