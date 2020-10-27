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

  def selectF: Fragment =
    fr"SELECT id, name, annotation_project_id, campaign_id, idx FROM" ++ tableF

  def insertAnnotationLabelClassGroup(
      groupCreate: AnnotationLabelClassGroup.Create,
      annotationProject: Option[AnnotationProject],
      campaign: Option[Campaign],
      indexFallback: Int,
      parentAnnotationLabelClasses: List[AnnotationLabelClass] = Nil
  ): ConnectionIO[AnnotationLabelClassGroup.WithLabelClasses] = {
    val index = groupCreate.index getOrElse indexFallback
    val groupIO = (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, name, annotation_project_id, campaign_id, idx) VALUES (
        uuid_generate_v4(), ${groupCreate.name}, ${annotationProject.map(_.id)}, ${campaign
      .map(_.id)}, ${index}
      )""").update.withUniqueGeneratedKeys[AnnotationLabelClassGroup](
      "id",
      "name",
      "annotation_project_id",
      "campaign_id",
      "idx"
    )
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

  def deleteByProjectId(
      projectId: UUID
  ): ConnectionIO[Int] =
    query.filter(fr"annotation_project_id = $projectId").delete

  def deleteByCampaignId(
      campaignId: UUID
  ): ConnectionIO[Int] =
    query.filter(fr"campaignId = $campaignId").delete
}
