package com.rasterfoundry.database

import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

object AnnotationLabelClassGroupDao extends Dao[AnnotationLabelClassGroup] {
  val tableName = "annotation_label_class_groups"

  def selectF: Fragment =
    fr"SELECT id, name, annotation_project_id, idx FROM" ++ tableF

  def insertAnnotationLabeClassGroup(
      groupCreate: AnnotationLabelClassGroup.Create,
      annotationProject: AnnotationProject,
      indexFallback: Int
  ): ConnectionIO[AnnotationLabelClassGroup] = {
    val index = groupCreate.index getOrElse indexFallback
    val groupIO = (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, name, annotation_project_id, idx) VALUES (
        uuid_generate_v4(), ${groupCreate.name}, ${annotationProject.id}, ${index}
      )""").update.withUniqueGeneratedKeys[AnnotationLabelClassGroup](
      "id",
      "name",
      "annotation_project_id",
      "idx"
    )
    for {
      labelClassGroup <- groupIO
      _ <- groupCreate.classes traverse { labelClass =>
        AnnotationLabelClassDao.insertAnnotationLabelClass(
          labelClass,
          labelClassGroup
        )
      }
    } yield labelClassGroup
  }
}
