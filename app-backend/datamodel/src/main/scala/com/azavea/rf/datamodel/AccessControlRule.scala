package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

@JsonCodec
case class AccessControlRule(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  isActive: Boolean,
  objectType: ObjectType,
  objectId: UUID,
  subjectType: SubjectType,
  subjectId: Option[String],
  actionType: ActionType
)

object AccessControlRule {
  def create = Create.apply _
  def tupled = (AccessControlRule.apply _).tupled

  @JsonCodec
  case class Create(
    isActive: Boolean,
    subjectType: SubjectType,
    subjectId: Option[String],
    actionType: ActionType
  ) {
    def toAccessControlRule(user: User, objectType: ObjectType, objectId: UUID): AccessControlRule = {
      val now = new Timestamp((new java.util.Date()).getTime())
      AccessControlRule(
        UUID.randomUUID(),
        now, // createdAt
        user.id, // createdBy
        isActive,
        objectType,
        objectId,
        subjectType,
        subjectId,
        actionType
      )
    }
  }
}

@JsonCodec
case class ObjectAccessControlRule(
  subjectType: SubjectType,
  subjectId: Option[String],
  actionType: ActionType
) {
  def toObjAcrString: String = (subjectType, subjectId) match {
    case (SubjectType.All, None) => s"ALL;;${actionType}"
    case (SubjectType.All, Some(subjectIdString)) => throw new Exception(s"Invalid subjectId for subjectType All: ${subjectIdString}")
    case (_, None) => throw new Exception(s"No subject Id provided for subject type: ${subjectType}")
    case (_, Some(subjectIdString)) => s"${subjectType};${subjectIdString};${actionType}"
  }
}

object ObjectAccessControlRule {
  def fromObjAcrString(objAcrString: String): Option[ObjectAccessControlRule] = objAcrString.split(";") match {
    case Array(subjectType, subjectId, actionType) => (subjectType, subjectId.length) match {
      case ("ALL", 0) => Some(ObjectAccessControlRule(SubjectType.All, None, ActionType.fromString(actionType)))
      case ("ALL", _) => throw new Exception(s"Invalid subjectId for subjectType All: ${subjectId}")
      case (_, 0) => throw new Exception(s"No subject Id provided for subject type: ${subjectType}")
      case (_, _) => Some(ObjectAccessControlRule(
        SubjectType.fromString(subjectType),
        Some(subjectId),
        ActionType.fromString(actionType)
      ))
    }
    case _ => None
  }

  def unsafeFromObjAcrString(objAcrString: String): ObjectAccessControlRule =
    fromObjAcrString(objAcrString).getOrElse(throw new Exception("Invalid format: " + objAcrString))
}
