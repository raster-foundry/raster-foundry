package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.filter.Filters
import com.rasterfoundry.database.util.Page
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.{Order, PageRequest}

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import io.circe._
import io.circe.syntax._

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
      annotationO <- query
        .filter(fr"project_id = ${projectId}")
        .filter(annotationId)
        .selectOption
      ownerIO = annotationO match {
        case Some(annotation) => UserDao.getUserById(annotation.owner)
        case None             => None.pure[ConnectionIO]
      }
      ownerO <- ownerIO
    } yield {
      (annotationO, ownerO) match {
        case (Some(annotation), Some(owner)) =>
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

  def listForExport(
      projectF: Fragment,
      layerF: Fragment
  ): ConnectionIO[List[Annotation]] =
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

  def listForLayerExport(
      projectId: UUID,
      layerId: UUID
  ): ConnectionIO[List[Annotation]] =
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
            .flatMap(
              defaultId =>
                ProjectDao
                  .updateProject(
                    project.copy(defaultAnnotationGroup = Some(defaultId)),
                    project.id
                  )
                  .map(_ => defaultId)
            )
      }
      annotationFragments: List[Fragment] = annotations.map(
        (annotationCreate: Annotation.Create) => {
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
        }
      )
      insertedAnnotations <- annotationFragments.toNel
        .map(
          fragments =>
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
      projectLayerIdO: Option[UUID] = None
  ): ConnectionIO[Int] =
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
            val fragments = bboxPolygons.map(
              bbox =>
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
      page <- (selectF ++ fromF ++ Fragments.whereAndOpt(filters: _*) ++ Page(
        pageRequest.copy(
          sort = pageRequest.sort ++ Map(
            "a.modified_at" -> Order.Desc,
            "a.id" -> Order.Desc
          )
        )
      )).query[AnnotationWithOwnerInfo]
        .to[List]
      count <- (countF ++ Fragments.whereAndOpt(filters: _*))
        .query[Int]
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

  def listDetectionLayerAnnotationsByTaskStatus(
      projectId: UUID,
      layerId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[List[Annotation]] = {
    val taskStatusF: Fragment =
      taskStatuses.map(TaskStatus.fromString(_)).toNel match {
        case Some(taskStatusNel) =>
          fr"AND" ++ Fragments.in(fr"tasks.status", taskStatusNel)
        case _ => fr""
      }
    (fr"""
      SELECT
        annotations.id,
        annotations.project_id,
        annotations.created_at,
        annotations.created_by,
        annotations.modified_at,
        annotations.owner,
        project_labels.name,
        annotations.description,
        annotations.machine_generated,
        annotations.confidence,
        annotations.quality,
        annotations.geometry,
        annotations.annotation_group,
        annotations.labeled_by,
        annotations.verified_by,
        annotations.project_layer_id,
        annotations.task_id
      FROM annotations
      JOIN annotation_groups AS ag
      ON ag.id = annotations.annotation_group
        AND ag.project_id = ${projectId}
        AND ag.project_layer_id = ${layerId}
        AND ag.name = 'label'
      JOIN tasks
      ON tasks.id = annotations.task_id
         AND tasks.project_id = ${projectId}
         AND tasks.project_layer_id = ${layerId}
         AND annotations.project_id = ${projectId}
         AND annotations.project_layer_id = ${layerId}
      """ ++ taskStatusF ++ fr"""
      JOIN (
        SELECT labels.id, labels.name
        FROM
            projects,
            jsonb_to_recordset((projects.extras->'annotate')::jsonb ->'labels') AS labels(id uuid, name text)
        WHERE projects.id = ${projectId}
      ) project_labels
      ON project_labels.id::uuid = annotations.label::uuid
       """)
      .query[Annotation]
      .to[List]
  }

  /** List Chip Classification Annotations with Classes as GeoJSON FeatureCollection
    *
    * It filters annotations by project ID, layer ID, and by task statuses
    * The classes in each annotation are in the below shape
    *  {
    *      "Building": "Mapped",
    *      "Road": "Partially Mapped"
    *  }
    */
  def listClassificationLayerAnnotationsByTaskStatus(
      projectId: UUID,
      layerId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[AnnotationWithClassesFeatureCollection] = {
    val taskStatusF: Fragment =
      taskStatuses.map(TaskStatus.fromString(_)).toNel match {
        case Some(taskStatusNel) =>
          fr"AND" ++ Fragments.in(fr"tasks.status", taskStatusNel)
        case _ => fr""
      }

    // Select chip classification annotations based on project Id, layerId,
    // and task statuses
    val annotationTaskFilterF: Fragment = fr"""
      SELECT annotations.*
        FROM annotations
        JOIN annotation_groups AS ag
        ON
            ag.id = annotations.annotation_group
            AND ag.project_id = ${projectId}
            AND ag.project_layer_id = ${layerId}
            AND ag.name = 'label'
        JOIN tasks
        ON annotations.task_id = tasks.id
        WHERE
            tasks.project_id = ${projectId}
            AND tasks.project_layer_id = ${layerId}
            AND annotations.project_id = ${projectId}
            AND annotations.project_layer_id = ${layerId}
            """ ++ taskStatusF

    // Select predefined project label names and their catagory/group ids
    val projectLabelsF: Fragment = fr"""
      (
        SELECT uuid(labels.id) as id, labels.name, uuid(labels."labelGroup") AS label_group_id
        FROM
          projects,
          jsonb_to_recordset((projects.extras->'annotate')::jsonb ->'labels') AS  labels(id uuid, name text, "labelGroup" uuid)
        WHERE projects.id = ${projectId}
      ) AS project_labels
    """

    // Select predefined project label catagory/group IDs and names
    val projectCatagoriesF: Fragment = fr"""
      (
        SELECT uuid(labelGroups.key) as id, labelGroups.value AS name
        FROM
          projects,
          jsonb_each_text((projects.extras->'annotate')::jsonb ->'labelGroups') as labelGroups
        WHERE projects.id = ${projectId}
      ) AS project_label_groups
    """

    // Select project Label name and catagry/group name
    val projectCatagoryAndLabelF: Fragment = fr"""
      (
        SELECT
          project_labels.id,
          project_labels.name AS label_name,
          project_label_groups.name AS group_name
        FROM
      """ ++
      projectLabelsF ++
      fr"LEFT JOIN" ++
      projectCatagoriesF ++
      fr"""
          ON project_labels.label_group_id::uuid = project_label_groups.id::uuid
        ) AS project_label_and_groups
        """

    // The last column is a JSON field in the below format:
    // {
    //   "Building": "Mapped",
    //   "Road": "Partially Mapped"
    // }
    (fr"WITH filtered_annotation AS (" ++ annotationTaskFilterF ++ fr""")
      SELECT
        filtered_annotation.id,
        filtered_annotation.project_id,
        filtered_annotation.created_at,
        filtered_annotation.created_by,
        filtered_annotation.modified_at,
        filtered_annotation.owner,
        filtered_annotation.description,
        filtered_annotation.machine_generated,
        filtered_annotation.confidence,
        filtered_annotation.quality,
        filtered_annotation.geometry,
        filtered_annotation.annotation_group,
        filtered_annotation.labeled_by,
        filtered_annotation.verified_by,
        filtered_annotation.project_layer_id,
        filtered_annotation.task_id,
        jsonb_object(string_to_array(string_agg(
          CONCAT(
            project_label_and_groups.group_name, ',', project_label_and_groups.label_name
          ),','),',')::text[]) as classes
      FROM
        filtered_annotation,
        unnest(string_to_array(filtered_annotation.label, ' ')) AS label(class)
      JOIN
      """ ++ projectCatagoryAndLabelF ++ fr"""
      ON project_label_and_groups.id::uuid = label.class::uuid
      GROUP BY (
        filtered_annotation.id,
        filtered_annotation.project_id,
        filtered_annotation.created_at,
        filtered_annotation.created_by,
        filtered_annotation.modified_at,
        filtered_annotation.owner,
        filtered_annotation.description,
        filtered_annotation.machine_generated,
        filtered_annotation.confidence,
        filtered_annotation.quality,
        filtered_annotation.geometry,
        filtered_annotation.annotation_group,
        filtered_annotation.labeled_by,
        filtered_annotation.verified_by,
        filtered_annotation.project_layer_id,
        filtered_annotation.task_id
      )
    """)
      .query[AnnotationWithClasses]
      .to[List]
      .map(annoteWithClassesList => {
        AnnotationWithClassesFeatureCollection(
          annoteWithClassesList.map(_.toGeoJSONFeature)
        )
      })
  }

  def listSegmentationLayerAnnotationsByTaskStatus(
      projectId: UUID,
      layerId: UUID,
      taskStatuses: List[String]
  ): ConnectionIO[List[Annotation]] = {
    val taskStatusF: Fragment =
      taskStatuses.map(TaskStatus.fromString(_)).toNel match {
        case Some(taskStatusNel) =>
          fr"AND" ++ Fragments.in(fr"tasks.status", taskStatusNel)
        case _ => fr""
      }
    (fr"""
    SELECT
      annotations.id,
      annotations.project_id,
      annotations.created_at,
      annotations.created_by,
      annotations.modified_at,
      annotations.owner,
      project_labels.name,
      annotations.description,
      annotations.machine_generated,
      annotations.confidence,
      annotations.quality,
      ST_INTERSECTION(ST_MAKEVALID(annotations.geometry), tasks.geometry) as geometry,
      annotations.annotation_group,
      annotations.labeled_by,
      annotations.verified_by,
      annotations.project_layer_id,
      annotations.task_id
    FROM annotations
    JOIN annotation_groups AS ag
    ON ag.id = annotations.annotation_group
      AND ag.project_id = ${projectId}
      AND ag.project_layer_id = ${layerId}
      AND ag.name = 'label'
    JOIN tasks
    ON tasks.id = annotations.task_id
       AND tasks.project_id = ${projectId}
       AND tasks.project_layer_id = ${layerId}
       AND annotations.project_id = ${projectId}
       AND annotations.project_layer_id = ${layerId}
    """ ++ taskStatusF ++ fr"""
    JOIN (
      SELECT labels.id, labels.name
      FROM
          projects,
          jsonb_to_recordset((projects.extras->'annotate')::jsonb ->'labels') AS labels(id uuid, name text)
      WHERE projects.id = ${projectId}
    ) project_labels
    ON project_labels.id::uuid = annotations.label::uuid
     """)
      .query[Annotation]
      .to[List]
  }

  def getLayerAnnotationJsonByTaskStatus(
      projectId: UUID,
      layerId: UUID,
      taskStatuses: List[String],
      projectType: MLProjectType
  ): ConnectionIO[Option[Json]] = projectType match {
    case MLProjectType.ChipClassification =>
      listClassificationLayerAnnotationsByTaskStatus(
        projectId,
        layerId,
        taskStatuses
      ).map(annoFC => Some(annoFC.asJson))
    case MLProjectType.ObjectDetection =>
      listDetectionLayerAnnotationsByTaskStatus(
        projectId,
        layerId,
        taskStatuses
      ).map(annotations => {
        Some(
          AnnotationFeatureCollection(annotations.map(_.toGeoJSONFeature)).asJson
        )
      })
    case MLProjectType.SemanticSegmentation =>
      listSegmentationLayerAnnotationsByTaskStatus(
        projectId,
        layerId,
        taskStatuses
      ).map(annotations => {
        Some(
          AnnotationFeatureCollection(annotations.map(_.toGeoJSONFeature)).asJson
        )
      })
    case _ => Option.empty.pure[ConnectionIO]
  }
}
