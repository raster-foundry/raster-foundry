package com.rasterfoundry.database

import java.sql.Timestamp
import java.util.UUID

import cats.implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

object AnnotationGroupDao extends Dao[AnnotationGroup] {

  val tableName = "annotation_groups"

  val selectF: Fragment =
    fr"SELECT id, name, created_at, created_by, modified_at, modified_by, project_id, default_style from" ++ tableF

  def unsafeGetAnnotationGroupById(
      groupId: UUID): ConnectionIO[AnnotationGroup] = {
    query.filter(groupId).select
  }

  def listAnnotationGroupsForProject(
      projectId: UUID): ConnectionIO[List[AnnotationGroup]] = {
    (selectF ++ Fragments.whereAndOpt(fr"project_id = ${projectId}".some))
      .query[AnnotationGroup]
      .stream
      .compile
      .toList
  }

  def insertAnnotationGroup(
      ag: AnnotationGroup
  ): ConnectionIO[AnnotationGroup] = {
    fr"""
    INSERT INTO annotation_groups (
    id, name, created_at, created_by, modified_at, modified_by, project_id, default_style
    )
    VALUES
    (${ag.id}, ${ag.name}, ${ag.createdAt}, ${ag.createdBy}, ${ag.modifiedAt},
     ${ag.modifiedBy}, ${ag.projectId}, ${ag.defaultStyle})
    """.update.withUniqueGeneratedKeys[AnnotationGroup](
      "id",
      "name",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "project_id",
      "default_style"
    )
  }
  def updateAnnotationGroupQ(annotationGroup: AnnotationGroup,
                             id: UUID,
                             user: User): Update0 = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${id}"

    val query = (fr"UPDATE" ++ tableF ++ fr"""SET
       modified_at = ${updateTime},
       modified_by = ${user.id},
       name = ${annotationGroup.name},
       default_style = ${annotationGroup.defaultStyle}
    """ ++ Fragments.whereAndOpt(Some(idFilter))).update
    query
  }

  def createAnnotationGroup(
      projectId: UUID,
      agCreate: AnnotationGroup.Create,
      user: User
  ): ConnectionIO[AnnotationGroup] =
    insertAnnotationGroup(agCreate.toAnnotationGroup(projectId, user))

  def getAnnotationGroup(projectId: UUID,
                         agId: UUID): ConnectionIO[Option[AnnotationGroup]] =
    query.filter(fr"project_id = $projectId").filter(agId).selectOption

  def getAnnotationGroupSummary(
      annotationGroupId: UUID): ConnectionIO[List[LabelSummary]] = {
    val queryString =
      sql"""
           SELECT
               annots.label, jsonb_object_agg(annots.quality, coalesce(counts.count, 0)) as counts
           FROM (
               SELECT
                   DISTINCT annotation_group,
                   label,
                   vals.quality
               FROM
                   annotations,
                   (
                       SELECT
                           unnest(enum_range(NULL::annotation_quality)) AS quality) AS vals
              WHERE annotation_group = ${annotationGroupId}
              ) AS annots
               LEFT JOIN (
                   SELECT
                       annotation_group,
                       count(*),
                       label,
                       quality
                   FROM
                       annotations
                   WHERE
                       annotation_group = ${annotationGroupId}
                   GROUP BY
                       quality,
                       label,
                       annotation_group) counts ON annots.annotation_group = counts.annotation_group
               AND counts.label = annots.label
               AND annots.quality = counts.quality
           GROUP BY annots.label

         """
    queryString.query[LabelSummary].to[List]
  }

  def deleteAnnotationGroup(projectId: UUID, agId: UUID): ConnectionIO[Int] =
    for {
      _ <- AnnotationDao.deleteByAnnotationGroup(agId)
      deleteCount <- query
        .filter(fr"project_id = $projectId")
        .filter(agId)
        .delete
    } yield deleteCount

  def updateAnnotationGroup(ag: AnnotationGroup,
                            agId: UUID,
                            user: User): ConnectionIO[Int] = {
    updateAnnotationGroupQ(ag, agId, user).run
  }

}
