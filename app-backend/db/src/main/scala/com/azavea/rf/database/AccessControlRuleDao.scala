package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
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
            is_active,
            object_type, object_id, subject_type,
            subject_id, action_type
        FROM
    """ ++ tableF

  def createF(ac: AccessControlRule) =
    fr"INSERT INTO" ++ tableF ++ fr"""(
            id, created_at, created_by,
            is_active,
            object_type, object_id, subject_type,
            subject_id, action_type
        ) VALUES (
            ${ac.id}, ${ac.createdAt}, ${ac.createdBy},
            ${ac.isActive},
            ${ac.objectType}, ${ac.objectId}, ${ac.subjectType},
            ${ac.subjectId}, ${ac.actionType}
        )
        """

  def listedByObject(objectType: ObjectType, objectId: UUID): Dao.QueryBuilder[AccessControlRule] =
    query.filter(fr"object_type = ${objectType}").filter(fr"object_id = ${objectId}")

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
        TeamDao.query.filter(UUID.fromString(sid)).exists
      case (SubjectType.User, Some(sid)) =>
        UserDao.filterById(sid).exists
      case _ => throw new Exception("Subject id required and but not provided")
    }

    val create = createF(ac).update.withUniqueGeneratedKeys[AccessControlRule](
      "id",
      "created_at",
      "created_by",
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

  def createMany(acrs: List[AccessControlRule]): ConnectionIO[Int] = {
    val acrFragments = acrs map {
      (acr: AccessControlRule) => fr"""
      (${acr.id}, ${acr.createdAt}, ${acr.createdBy}, ${acr.isActive},
      ${acr.objectType}, ${acr.objectId}, ${acr.subjectType}, ${acr.subjectId},
      ${acr.actionType})
      """
    }

    val acrValues = acrFragments.toNel match {
      case Some(fragments) => fragments.intercalate(fr",")
      case _ =>
        throw new IllegalArgumentException("Can't replace ACRs from an empty list")
    }

    val insertManyFragment = {
      fr"INSERT INTO" ++ tableF ++
        fr"(id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type) VALUES" ++
       acrValues
    }

    insertManyFragment.update.run
  }

  def createWithResults(acr: AccessControlRule): ConnectionIO[List[AccessControlRule]] = {
    create(acr) >>= {
      (dbAcr: AccessControlRule) => {
        listByObject(dbAcr.objectType, dbAcr.objectId)
      }
    }
  }

  // Delay materializing the list of ACR creates to guarantee that everything inserted has
  // the same object type and id
  def replaceWithResults(user: User, objectType: ObjectType, objectId: UUID, acrs: List[AccessControlRule.Create]):
      ConnectionIO[List[AccessControlRule]] = {
    val materialized = acrs map { _.toAccessControlRule(user, objectType, objectId) }

    listedByObject(objectType, objectId).delete >> createMany(materialized) >> listByObject(objectType, objectId)
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

  def listByObject(objectType: ObjectType, objectId: UUID): ConnectionIO[List[AccessControlRule]] =
    listedByObject(objectType, objectId).list

  def deactivateBySubject(subjectType: SubjectType, subjectId: String): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"""SET
      is_active = false
    """ ++ Fragments.whereAnd(fr"subject_type = ${subjectType}", fr"subject_id = ${subjectId}")).update.run
  }

  def listUserActionsF(user: User, objectType: ObjectType, objectId: UUID): Fragment = {
    fr"""
    SELECT DISTINCT action_type
    FROM access_control_rules
    WHERE
      is_active = true
      AND
      object_type = ${objectType}
      AND
      UUID(object_id) = ${objectId}
      AND (
        subject_type = 'ALL'
        OR
        subject_id IN (
          SELECT text(group_id)
          FROM user_group_roles
          WHERE user_id = ${user.id}
        )
        OR
        subject_id = ${user.id}
      )
    """

  }

  def listUserActions(user: User, objectType: ObjectType, objectId: UUID): ConnectionIO[List[String]] =
    listUserActionsF(user, objectType, objectId).query[String].list

}
