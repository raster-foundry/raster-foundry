package com.rasterfoundry.database

import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._

import java.sql.Timestamp
import java.util.UUID

object AnnotationGroupDao extends Dao[AnnotationGroup] {

  val tableName = "annotation_groups"

  val selectF: Fragment =
    fr"""
      SELECT
        id, name, created_at, created_by, modified_at,
        project_id, default_style, project_layer_id
      FROM
    """ ++ tableF

  def unsafeGetAnnotationGroupById(
      groupId: UUID
  ): ConnectionIO[AnnotationGroup] = {
    query.filter(groupId).select
  }

  // look for default project layer if projectLayerIdO is not provided
  def listForProject(
      projectId: UUID,
      projectLayerIdO: Option[UUID] = None
  ): ConnectionIO[List[AnnotationGroup]] = {
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = ProjectDao.getProjectLayerId(projectLayerIdO, project)
      agList <- (selectF ++ Fragments.whereAndOpt(
        Some(fr"project_id = ${projectId}"),
        Some(fr"project_layer_id = ${projectLayerId}")
      )).query[AnnotationGroup].stream.compile.toList
    } yield { agList }
  }

  def insertAnnotationGroup(
      ag: AnnotationGroup
  ): ConnectionIO[AnnotationGroup] = {
    fr"""
    INSERT INTO annotation_groups (
    id, name, created_at, created_by, modified_at,
    project_id, default_style, project_layer_id
    )
    VALUES
    (${ag.id}, ${ag.name}, ${ag.createdAt}, ${ag.createdBy}, ${ag.modifiedAt},
     ${ag.projectId}, ${ag.defaultStyle}, ${ag.projectLayerId})
    """.update.withUniqueGeneratedKeys[AnnotationGroup](
      "id",
      "name",
      "created_at",
      "created_by",
      "modified_at",
      "project_id",
      "default_style",
      "project_layer_id"
    )
  }
  def updateAnnotationGroupQ(
      projectId: UUID,
      annotationGroup: AnnotationGroup,
      id: UUID
  ): Update0 = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${id}"
    val projectFilter = fr"project_id = ${projectId}"

    (fr"UPDATE" ++ tableF ++ fr"""SET
       modified_at = ${updateTime},
       name = ${annotationGroup.name},
       default_style = ${annotationGroup.defaultStyle},
       project_layer_id = ${annotationGroup.projectLayerId}
    """ ++ Fragments.whereAndOpt(Some(idFilter), Some(projectFilter))).update
  }

  // use default project layer if projectLayerIdO is not provided
  def createAnnotationGroup(
      projectId: UUID,
      agCreate: AnnotationGroup.Create,
      user: User,
      projectLayerIdO: Option[UUID] = None
  ): ConnectionIO[AnnotationGroup] =
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = ProjectDao.getProjectLayerId(projectLayerIdO, project)
      insertedAG <- insertAnnotationGroup(
        agCreate.toAnnotationGroup(projectId, user, projectLayerId)
      )
    } yield { insertedAG }

  def getAnnotationGroup(
      projectId: UUID,
      agId: UUID
  ): ConnectionIO[Option[AnnotationGroup]] =
    query.filter(fr"project_id = $projectId").filter(agId).selectOption

  def getAnnotationGroupSummaryF(
      annotationGroupId: UUID
  ): Fragment = sql"""
    SELECT
        annots.label, jsonb_object_agg(annots.quality, coalesce(counts.count, 0)) as counts
    FROM (
        SELECT
            distinct(label),
            vals.quality
        FROM
            annotations,
            (
                SELECT
                    unnest(enum_range(NULL::annotation_quality)) AS quality
            ) AS vals
        WHERE annotation_group = ${annotationGroupId}
    ) AS annots
    LEFT JOIN
    (
        SELECT
            count(*),
            label,
            coalesce(quality, 'YES'::annotation_quality) as quality
        FROM
            annotations
        WHERE
            annotation_group = ${annotationGroupId}
        GROUP BY
            quality,
            label,
            annotation_group
    ) counts ON annots.label = counts.label
    AND counts.label = annots.label
    AND annots.quality = counts.quality
    GROUP BY annots.label
  """

  // get annotation group summary by project layer
  // if layerId not provided, look at the default project layer
  def getAnnotationGroupSummary(
      annotationGroupId: UUID
  ): ConnectionIO[List[LabelSummary]] =
    getAnnotationGroupSummaryF(annotationGroupId)
      .query[LabelSummary]
      .to[List]

  def deleteAnnotationGroup(projectId: UUID, agId: UUID): ConnectionIO[Int] =
    for {
      _ <- AnnotationDao.deleteByAnnotationGroup(agId)
      deleteCount <- query
        .filter(fr"project_id = $projectId")
        .filter(agId)
        .delete
    } yield deleteCount

  def updateAnnotationGroup(
      projectId: UUID,
      ag: AnnotationGroup,
      agId: UUID
  ): ConnectionIO[Int] = {
    updateAnnotationGroupQ(projectId, ag, agId).run
  }

  def annotationGroupIsInLayer(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID
  ): ConnectionIO[Boolean] =
    this.query
      .filter(fr"id = $annotationGroupId")
      .filter(fr"project_id = $projectId")
      .filter(fr"project_layer_id = $layerId")
      .exists

  def authAnnotationGroupExists(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID,
      user: User,
      actionType: ActionType
  ): ConnectionIO[Boolean] =
    for {
      authProject <- ProjectDao.authorized(
        user,
        ObjectType.Project,
        projectId,
        actionType
      )
      layerExists <- ProjectLayerDao.layerIsInProject(layerId, projectId)
      authAnnotationGroup <- annotationGroupIsInLayer(
        projectId,
        layerId,
        annotationGroupId
      )
    } yield { authProject.toBoolean && layerExists && authAnnotationGroup }
}
