package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object AnnotationLabelClassDao extends Dao[AnnotationLabelClass] {
  val tableName = "annotation_label_classes"

  override val fieldNames = List(
    "id",
    "name",
    "annotation_label_group_id",
    "color_hex_code",
    "is_default",
    "is_determinant",
    "idx",
    "geometry_type",
    "description"
  )

  def selectF: Fragment = fr"SELECT " ++ selectFieldsF ++ fr" FROM " ++ tableF

  def insertAnnotationLabelClass(
      classCreate: AnnotationLabelClass.Create,
      annotationLabelGroup: AnnotationLabelClassGroup
  ): ConnectionIO[AnnotationLabelClass] = {
    (fr"INSERT INTO" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
      fr"""VALUES (
        uuid_generate_v4(), ${classCreate.name}, ${annotationLabelGroup.id},
        ${classCreate.colorHexCode}, ${classCreate.default}, ${classCreate.determinant},
        ${classCreate.index}, ${classCreate.geometryType}, ${classCreate.description}
      )""").update.withUniqueGeneratedKeys[AnnotationLabelClass](fieldNames: _*)
  }

  def listAnnotationLabelClassByGroupId(
      groupId: UUID
  ): ConnectionIO[List[AnnotationLabelClass]] =
    query.filter(fr"annotation_label_group_id = ${groupId}").list
}
