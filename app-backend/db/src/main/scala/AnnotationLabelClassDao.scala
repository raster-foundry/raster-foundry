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

  def getById(id: UUID): ConnectionIO[Option[AnnotationLabelClass]] =
    query.filter(id).selectOption

  def listAnnotationLabelClassByGroupId(
      groupId: UUID
  ): ConnectionIO[List[AnnotationLabelClass]] =
    query.filter(fr"annotation_label_group_id = ${groupId}").list

  def update(id: UUID, labelClass: AnnotationLabelClass): ConnectionIO[Int] =
    (fr"UPDATE " ++ tableF ++ fr"""SET
      name = ${labelClass.name},
      color_hex_code = ${labelClass.colorHexCode},
      default = ${labelClass.default},
      determinant = ${labelClass.determinant},
      idx = ${labelClass.index},
      geometry_type = ${labelClass.geometryType},
      description = ${labelClass.description}
    WHERE
      id = $id
    """).update.run;

  def activate(id: UUID): ConnectionIO[Int] =
    (fr"UPDATE " ++ tableF ++ fr"""SET
      is_active = true
    WHERE
      id = $id
    """).update.run;

  def deactivate(id: UUID): ConnectionIO[Int] =
    (fr"UPDATE " ++ tableF ++ fr"""SET
      is_active = false
    WHERE
      id = $id
    """).update.run;
}
