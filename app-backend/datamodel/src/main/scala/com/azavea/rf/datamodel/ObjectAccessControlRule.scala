package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe.generic.JsonCodec

@JsonCodec
case class ObjectAccessControlRule(
    subjectType: SubjectType,
    subjectId: Option[String],
    actionType: ActionType
) {
  def toObjAcrString: String = (subjectType, subjectId) match {
    case (SubjectType.All, None) => s"ALL;;${actionType}"
    case (SubjectType.All, Some(subjectIdString)) =>
      throw new Exception(
        s"Invalid subjectId for subjectType All: ${subjectIdString}")
    case (_, None) =>
      throw new Exception(
        s"No subject Id provided for subject type: ${subjectType}")
    case (_, Some(subjectIdString)) =>
      s"${subjectType};${subjectIdString};${actionType}"
  }
}

object ObjectAccessControlRule {
  def fromObjAcrString(objAcrString: String): Option[ObjectAccessControlRule] =
    objAcrString.split(";") match {
      case Array(subjectType, subjectId, actionType) =>
        (subjectType, subjectId.length) match {
          case ("ALL", 0) =>
            Some(
              ObjectAccessControlRule(SubjectType.All,
                                      None,
                                      ActionType.fromString(actionType)))
          case ("ALL", _) =>
            throw new Exception(
              s"Invalid subjectId for subjectType All: ${subjectId}")
          case (_, 0) =>
            throw new Exception(
              s"No subject Id provided for subject type: ${subjectType}")
          case (_, _) =>
            Some(
              ObjectAccessControlRule(
                SubjectType.fromString(subjectType),
                Some(subjectId),
                ActionType.fromString(actionType)
              ))
        }
      case _ => None
    }

  def unsafeFromObjAcrString(objAcrString: String): ObjectAccessControlRule =
    fromObjAcrString(objAcrString).getOrElse(
      throw new Exception("Invalid format: " + objAcrString))
}
