package com.azavea.rf.database

import java.sql.Timestamp
import java.util.UUID

import cats.implicits._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util._
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
