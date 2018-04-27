package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.{
  AccessControlRule,
  ActionType,
  ObjectType,
  SubjectType,
  User
}

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._

import java.util.UUID

object AccessControlRuleDao extends Dao[AccessControlRule] {
  val tableName = "access_control_rules"

  val selectF = fr"""
        SELECT
            id, created_at, created_by,
            modified_at, modified_by, is_active,
            object_type, object_id, subject_type,
            subject_id, action_type
        FROM
    """ ++ tableF

  def createF(ac: AccessControlRule) =
    fr"INSERT INTO" ++ tableF ++ fr"""(
            id, created_at, created_by,
            modified_at, modified_by, is_active,
            object_type, object_id, subject_type,
            subject_id, action_type
        ) VALUES (
            ${ac.id}, ${ac.createdAt}, ${ac.createdBy},
            ${ac.modifiedAt}, ${ac.modifiedBy}, ${ac.isActive},
            ${ac.objectType}, ${ac.objectId}, ${ac.subjectType},
            ${ac.subjectId}, ${ac.actionType}
        )
        """

  def create(ac: AccessControlRule): ConnectionIO[AccessControlRule] = {
    val objectDao = ac.objectType match {
      case ObjectType.Project    => ProjectDao
      case ObjectType.Scene      => SceneDao
      case ObjectType.Datasource => DatasourceDao
      case ObjectType.Shape      => ShapeDao
      case ObjectType.Workspace =>
        throw new Exception("Workspaces not yet supported")
      case ObjectType.Template =>
        throw new Exception("Templates not yet supported")
      case ObjectType.Analysis =>
        throw new Exception("Analyses not yet supported")
    }

    val isValidObject = objectDao.query.filter(ac.objectId).exists

    // These validity checks need to be done individually because of varying id types

    val isValidSubject = (ac.subjectType, ac.subjectId) match {
      case (SubjectType.All, _) => true.pure[ConnectionIO]
      case (SubjectType.Platform, Some(sid)) =>
        PlatformDao.query.filter(UUID.fromString(sid)).exists
      case (SubjectType.Organization, Some(sid)) =>
        OrganizationDao.query.filter(UUID.fromString(sid)).exists
      case (SubjectType.Team, Some(sid)) =>
        OrganizationDao.query.filter(UUID.fromString(sid)).exists
      case (SubjectType.User, Some(sid)) =>
        UserDao.filterById(sid).exists
      case _ => throw new Exception("Subject id required and but not provided")
    }

    val create = createF(ac).update.withUniqueGeneratedKeys[AccessControlRule](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "is_active",
      "object_type",
      "object_id",
      "subject_type",
      "subject_id",
      "action_type"
    )

    for {
      isValidObject <- isValidObject
      isValidSubject <- isValidSubject
      createAC <- {
        if (isValidObject && isValidSubject) create
        else {
          val errSB = new StringBuilder("Invalid access control object")
          if (!isValidObject) errSB.append(", invalid object")
          if (!isValidSubject) errSB.append(", invalid subject")
          throw new Exception(errSB.toString)
        }
      }
    } yield createAC
  }

  def getOption(id: UUID): ConnectionIO[Option[AccessControlRule]] = {
    query.filter(id).selectOption
  }

  // List rules that a given subject has applied to it
  def listBySubject(
      subjectType: SubjectType,
      subjectId: String): ConnectionIO[List[AccessControlRule]] = {
    query
      .filter(fr"subject_type = ${subjectType}")
      .filter(fr"subject_id = ${subjectId}")
      .list
  }

  def deactivate(id: UUID, user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr""" SET
        is_active = false,
        modified_at = NOW(),
        modified_by = ${user.id}
      WHERE id = ${id}
    """).update.run
  }
}
