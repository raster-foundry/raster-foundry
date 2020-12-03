package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
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
    "description",
    "is_active"
  )

  def selectF: Fragment = fr"SELECT " ++ selectFieldsF ++ fr" FROM " ++ tableF

  def insertAnnotationLabelClass(
      classCreate: AnnotationLabelClass.Create,
      annotationLabelGroup: AnnotationLabelClassGroup,
      parent: Option[AnnotationLabelClass]
  ): ConnectionIO[AnnotationLabelClass] =
    for {
      newLabelClass <- (fr"INSERT INTO" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
        fr"""VALUES (
        uuid_generate_v4(), ${classCreate.name}, ${annotationLabelGroup.id},
        ${classCreate.colorHexCode}, ${classCreate.default}, ${classCreate.determinant},
        ${classCreate.index}, ${classCreate.geometryType}, ${classCreate.description},
        ${classCreate.isActive}
      )""").update.withUniqueGeneratedKeys[AnnotationLabelClass](fieldNames: _*)
      _ <- parent traverse { parentClass =>
        fr"INSERT INTO label_class_history VALUES (${parentClass.id}, ${newLabelClass.id})".update.run
      }
    } yield newLabelClass

  def listAnnotationLabelClassByGroupId(
      groupId: UUID
  ): ConnectionIO[List[AnnotationLabelClass]] =
    query.filter(fr"annotation_label_group_id = ${groupId}").list

  def activate(classId: UUID): ConnectionIO[Int] = sql"""
    update annotation_label_classes set is_active = TRUE where id = $classId
  """.update.run

  def deactivate(classId: UUID): ConnectionIO[Int] = sql"""
    update annotation_label_classes set is_active = FALSE where id = $classId
  """.update.run
}
