package com.rasterfoundry.database

import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object AnnotationLabelClassDao extends Dao[AnnotationLabelClass] {
  val tableName = "annotation_label_classes"

  def selectF: Fragment =
    fr"""
      SELECT
        id, name, annotation_label_group_id, color_hex_code, is_default,
        is_determinant, idx
      FROM""" ++ tableF

  def insertAnnotationLabelClass(
      classCreate: AnnotationLabelClass.Create,
      annotationLabelGroup: AnnotationLabelClassGroup
  ): ConnectionIO[AnnotationLabelClass] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, name, annotation_label_group_id, color_hex_code, is_default,
       is_determinant, idx)
      VALUES (
        uuid_generate_v4(), ${classCreate.name}, ${annotationLabelGroup.id},
        ${classCreate.colorHexCode}, ${classCreate.default}, ${classCreate.determinant},
        ${classCreate.index}
      )""").update.withUniqueGeneratedKeys[AnnotationLabelClass](
      "id",
      "name",
      "annotation_label_group_id",
      "color_hex_code",
      "is_default",
      "is_determinant",
      "idx"
    )
  }

  def listAnnotationLabelClassByGroupId(
      groupId: UUID
  ): ConnectionIO[List[AnnotationLabelClass]] = {
    (selectF ++ Fragments.whereAndOpt(
      Some(fr"annotation_label_group_id = ${groupId}")
    )).query[AnnotationLabelClass].to[List]
  }
}
