package com.rasterfoundry.database

import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object AnnotationLabelClassGroupDao
    extends Dao[AnnotationLabelClassGroup]
    with ConnectionIOLogger {
  val tableName = "annotation_label_class_groups"

  override val fieldNames = List(
    "id",
    "name",
    "annotation_project_id",
    "campaign_id",
    "idx",
    "is_active"
  )

  def selectF: Fragment = fr"SELECT " ++ selectFieldsF ++ fr" FROM " ++ tableF

  def insertAnnotationLabelClassGroup(
      groupCreate: AnnotationLabelClassGroup.Create,
      annotationProject: Option[AnnotationProject],
      campaign: Option[Campaign],
      indexFallback: Int,
      parentAnnotationLabelClasses: List[AnnotationLabelClass] = Nil
  ): ConnectionIO[AnnotationLabelClassGroup.WithLabelClasses] = {
    val index = groupCreate.index getOrElse indexFallback
    val groupIO = (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, name, annotation_project_id, campaign_id, idx, is_active) VALUES (
        uuid_generate_v4(), ${groupCreate.name}, ${annotationProject.map(
      _.id
    )}, ${campaign
      .map(_.id)}, ${index}, true
      )""").update
      .withUniqueGeneratedKeys[AnnotationLabelClassGroup](fieldNames: _*)
    for {
      labelClassGroup <- groupIO
      labelClasses <- parentAnnotationLabelClasses.toNel map { parentClasses =>
        parentClasses.toList.zip(groupCreate.classes) traverse {
          case (parentClass, labelClass) =>
            AnnotationLabelClassDao.insertAnnotationLabelClass(
              labelClass,
              labelClassGroup,
              parent = Some(parentClass)
            )
        }
      } getOrElse {
        groupCreate.classes traverse { labelClass =>
          AnnotationLabelClassDao.insertAnnotationLabelClass(
            labelClass,
            labelClassGroup,
            parent = None
          )
        }
      }
    } yield labelClassGroup.withLabelClasses(labelClasses)
  }

  def listByProjectId(
      projectId: UUID
  ): ConnectionIO[List[AnnotationLabelClassGroup]] = {
    (selectF ++ Fragments.whereAndOpt(
      Some(fr"annotation_project_id = ${projectId}")
    )).query[AnnotationLabelClassGroup].to[List]
  }

  def listByCampaignId(
      campaignId: UUID
  ): ConnectionIO[List[AnnotationLabelClassGroup]] = {
    (selectF ++ Fragments.whereAndOpt(
      Some(fr"campaign_id = ${campaignId}")
    )).query[AnnotationLabelClassGroup].to[List]
  }

  def listByProjectIdWithClasses(
      projectId: UUID
  ): ConnectionIO[List[AnnotationLabelClassGroup.WithLabelClasses]] =
    for {
      groups <- listByProjectId(projectId)
      groupsWithClasses <- groups traverse { group =>
        AnnotationLabelClassDao.listAnnotationLabelClassByGroupId(
          group.id
        ) map { classes =>
          group.withLabelClasses(classes)
        }
      }
    } yield groupsWithClasses

  def listByCampaignIdWithClasses(
      campaignId: UUID
  ): ConnectionIO[List[AnnotationLabelClassGroup.WithLabelClasses]] =
    for {
      groups <- listByCampaignId(campaignId)
      groupsWithClasses <- groups traverse { group =>
        AnnotationLabelClassDao.listAnnotationLabelClassByGroupId(
          group.id
        ) map { classes =>
          group.withLabelClasses(classes)
        }
      }
    } yield groupsWithClasses

  def getGroupWithClassesById(
      id: UUID
  ): ConnectionIO[Option[AnnotationLabelClassGroup.WithLabelClasses]] = {
    val groupIO = (selectF ++ Fragments.whereAndOpt(
      Some(fr"id = ${id}")
    )).query[AnnotationLabelClassGroup].option
    for {
      groupOpt <- groupIO
      classes <- AnnotationLabelClassDao.listAnnotationLabelClassByGroupId(id)
    } yield
      groupOpt map { group =>
        group.withLabelClasses(classes)
      }
  }

  def update(id: UUID, group: AnnotationLabelClassGroup): ConnectionIO[Int] =
    (fr"UPDATE " ++ tableF ++ fr"""SET
      name = ${group.name},
      idx = ${group.index},
      campaign_id = ${group.campaignId}
    WHERE
      id = $id
    """).update.run;

  def activate(id: UUID): ConnectionIO[AnnotationLabelClassGroup] =
    (fr"UPDATE " ++ tableF ++ fr"""SET
      is_active = true
    WHERE
      id = $id
    """).update
      .withUniqueGeneratedKeys[AnnotationLabelClassGroup](fieldNames: _*);

  def deactivate(id: UUID): ConnectionIO[Int] =
    (fr"UPDATE " ++ tableF ++ fr"""SET
      is_active = false
    WHERE
      id = $id
    """).update.run;

  def deleteByProjectId(
      projectId: UUID
  ): ConnectionIO[Int] =
    query.filter(fr"annotation_project_id = $projectId").delete

  def deleteByCampaignId(
      campaignId: UUID
  ): ConnectionIO[Int] =
    query.filter(fr"campaignId = $campaignId").delete
}
