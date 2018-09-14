package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

trait ObjectPermissions[Model] {
  def tableName: String

  def authQuery(user: User,
                objectType: ObjectType,
                ownershipTypeO: Option[String] = None,
                groupTypeO: Option[GroupType] = None,
                groupIdO: Option[UUID] = None): Dao.QueryBuilder[Model]

  def authorized(user: User,
                 objectType: ObjectType,
                 objectId: UUID,
                 actionType: ActionType): ConnectionIO[Boolean]

  def isValidObject(id: UUID): ConnectionIO[Boolean] =
    (tableName match {
      case "projects"    => ProjectDao
      case "scenes"      => SceneDao
      case "datasources" => DatasourceDao
      case "shapes"      => ShapeDao
      case "tool_runs"   => ToolRunDao
      case "tools"       => ToolDao
      case "workspaces" | "templates" | "analyses" =>
        throw new Exception(s"${tableName} not yet supported")
    }).query.filter(id).exists

  def isValidPermission(acr: ObjectAccessControlRule): ConnectionIO[Boolean] =
    (acr.subjectType, acr.subjectId) match {
      case (SubjectType.All, _) => true.pure[ConnectionIO]
      case (SubjectType.Platform, Some(subjectId)) =>
        PlatformDao.query.filter(UUID.fromString(subjectId)).exists
      case (SubjectType.Organization, Some(subjectId)) =>
        OrganizationDao.query.filter(UUID.fromString(subjectId)).exists
      case (SubjectType.Team, Some(subjectId)) =>
        TeamDao.query.filter(UUID.fromString(subjectId)).exists
      case (SubjectType.User, Some(subjectId)) =>
        UserDao.filterById(subjectId).exists
      case _ =>
        throw new Exception("Subject id required and but not provided in")
    }

  def getPermissionsF(id: UUID): Fragment =
    Fragment.const(s"SELECT acrs FROM ${tableName}") ++ Fragments.whereAndOpt(
      Some(fr"id = ${id}"))

  def appendPermissionF(id: UUID, acr: ObjectAccessControlRule): Fragment =
    Fragment.const(s"""
    UPDATE ${tableName}
    SET acrs = array_append(acrs, '${acr.toObjAcrString}'::text)
  """) ++ Fragments.whereAndOpt((Some(fr"id = ${id}")))

  def updatePermissionsF(id: UUID,
                         acrList: List[ObjectAccessControlRule],
                         replace: Boolean = false): Fragment = {
    val newAcrs: String = acrList.length match {
      case 0 => "'{}'::text[]"
      case _ =>
        val acrTextArray: String =
          s"ARRAY[${acrList.map("'" ++ _.toObjAcrString ++ "'").mkString(",")}]"
        if (replace) acrTextArray else s"array_cat(acrs, ${acrTextArray})"
    }
    Fragment.const(s"UPDATE ${tableName} SET acrs = ${newAcrs}") ++
      Fragments.whereAndOpt((Some(fr"id = ${id}")))
  }

  def listUserActionsF(user: User, id: UUID, groupIdsF: String): Fragment =
    Fragment.const(
      s"SELECT a.acrs from (SELECT UNNEST(acrs) acrs from ${tableName}") ++
      Fragments.whereAndOpt(Some(fr"id=${id}")) ++ Fragment.const(") a") ++
      Fragment.const(
        s"WHERE a.acrs LIKE '%${user.id}%' OR a.acrs LIKE '%ALL%' OR ${groupIdsF}")

  def acrStringsToList(
      acrs: List[String]): List[Option[ObjectAccessControlRule]] =
    acrs.map(ObjectAccessControlRule.fromObjAcrString)

  def getPermissions(
      id: UUID): ConnectionIO[List[Option[ObjectAccessControlRule]]] =
    for {
      isValidObject <- isValidObject(id)
      getPermissions <- isValidObject match {
        case false => throw new Exception(s"Invalid ${tableName} object ${id}")
        case true =>
          getPermissionsF(id)
            .query[List[String]]
            .unique
            .map(acrStringsToList(_))
      }
    } yield { getPermissions }

  def addPermission(id: UUID, acr: ObjectAccessControlRule)
    : ConnectionIO[List[Option[ObjectAccessControlRule]]] =
    isValidPermission(acr) flatMap { isValidPermission =>
      isValidPermission match {
        case false => throw new Exception(s"${acr.toObjAcrString} is invalid!")
        case true =>
          for {
            permissions <- getPermissions(id)
            permExists = permissions.contains(Some(acr))
            addPermission <- permExists match {
              case true =>
                throw new Exception(
                  s"${acr.toObjAcrString} exists for ${tableName} ${id}")
              case false =>
                appendPermissionF(id, acr).update
                  .withUniqueGeneratedKeys[List[String]]("acrs")
                  .map(acrStringsToList(_))
            }
          } yield { addPermission }
      }
    }

  def addPermissionsMany(id: UUID,
                         acrList: List[ObjectAccessControlRule],
                         replace: Boolean = false)
    : ConnectionIO[List[Option[ObjectAccessControlRule]]] = {
    val isAcrListPermittedIO: ConnectionIO[List[Boolean]] =
      acrList.traverse(isValidPermission(_))
    for {
      isAcrListPermitted <- isAcrListPermittedIO
      permissions <- getPermissions(id)
      acrListFiltered: List[ObjectAccessControlRule] = isAcrListPermitted.zipWithIndex
        .foldLeft(List[ObjectAccessControlRule]()) { (acc, perm) =>
          {
            val (permitted, idx): (Boolean, Int) = perm
            (replace, permitted) match {
              case (true, true) => acrList(perm._2) :: acc
              case (false, true)
                  if !permissions.contains(Some(acrList(perm._2))) =>
                acrList(perm._2) :: acc
              case _ =>
                acc
            }
          }
        }
      addPermissionsMany <- acrListFiltered.length match {
        case 0 if !replace =>
          throw new Exception(s"All permissions exist for ${tableName} ${id}")
        case 0 if replace =>
          throw new Exception("List of permissions do not have valid subjects")
        case _ =>
          updatePermissionsF(id, acrListFiltered, replace).update
            .withUniqueGeneratedKeys[List[String]]("acrs")
            .map(acrStringsToList(_))
      }
    } yield { addPermissionsMany }
  }

  def replacePermissions(id: UUID, acrList: List[ObjectAccessControlRule])
    : ConnectionIO[List[Option[ObjectAccessControlRule]]] =
    addPermissionsMany(id, acrList, true)

  def deletePermissions(
      id: UUID): ConnectionIO[List[Option[ObjectAccessControlRule]]] =
    updatePermissionsF(id, List[ObjectAccessControlRule]()).update
      .withUniqueGeneratedKeys[List[String]]("acrs")
      .map(acrStringsToList(_))

  def listUserActions(user: User, id: UUID): ConnectionIO[List[String]] =
    for {
      ugrs <- UserGroupRoleDao.listByUser(user)
      groupIdString = ugrs
        .map((ugr: UserGroupRole) => s"a.acrs LIKE '%${ugr.groupId.toString}%'")
        .mkString(" OR ")
      listUserActions <- listUserActionsF(user, id, groupIdString)
        .query[String]
        .to[List]
      actions = acrStringsToList(listUserActions).flatten
        .map(_.actionType.toString)
        .distinct
    } yield { actions }

  // TODO: in card #4020
  // def deactivateBySubject(subjectType: SubjectType, subjectId: String)

  def createVisibilityF(objectType: ObjectType,
                        actionType: ActionType): Fragment =
    (objectType, actionType) match {
      case (ObjectType.Shape, ActionType.View) =>
        Fragment.const("")
      case (_, ActionType.View) | (ObjectType.Scene, ActionType.Download) |
          (ObjectType.Project, ActionType.Export) |
          (ObjectType.Project, ActionType.Annotate) |
          (ObjectType.Analysis, ActionType.Export) =>
        Fragment.const("visibility = 'PUBLIC' OR")
      case _ =>
        Fragment.const("")
    }

  def createInheritedF(user: User,
                       actionType: ActionType,
                       groupTypeO: Option[GroupType],
                       groupIdO: Option[UUID]): Fragment =
    Fragment.const(s"""ARRAY(
    SELECT concat_ws(';', group_type, group_id, '${actionType.toString}')
    FROM user_group_roles
    WHERE user_id = '${user.id}'
    """) ++ (
      (groupTypeO, groupIdO) match {
        case (Some(groupType), Some(groupId)) =>
          fr"AND group_type = ${groupType.toString}::group_type AND group_id = ${groupId})"
        case _ =>
          fr")"
      }
    )

  def queryObjectsF(user: User,
                    objectType: ObjectType,
                    actionType: ActionType,
                    ownershipTypeO: Option[String] = None,
                    groupTypeO: Option[GroupType] = None,
                    groupIdO: Option[UUID] = None): Option[Fragment] = {
    val ownedF: Fragment =
      Fragment.const(s"owner = '${user.id}'")
    val visibilityF: Fragment =
      createVisibilityF(objectType, actionType)
    val sharedF: Fragment =
      Fragment.const(
        s"""ARRAY['ALL;;${actionType.toString}', 'USER;${user.id};${actionType.toString}']""")
    val inheritedF: Fragment =
      createInheritedF(user, actionType, groupTypeO, groupIdO)
    val acrFilterF
      : Fragment = fr"array_cat(" ++ sharedF ++ fr"," ++ inheritedF ++ fr") && acrs"

    ownershipTypeO match {
      // owned by the requesting user only
      case Some(ownershipType) if ownershipType == "owned" =>
        Some(ownedF)
      // shared to the requesting user directly, across platform, or due to group membership
      case Some(ownershipType) if ownershipType == "shared" =>
        Some(
          fr"(" ++ visibilityF ++ acrFilterF ++ fr") AND owner <> ${user.id}")
      // shared to the requesting user due to group membership
      case Some(ownershipType) if ownershipType == "inherited" =>
        Some(inheritedF ++ fr"&& acrs")
      // the default
      case _ =>
        Some(fr"(" ++ ownedF ++ fr"OR" ++ visibilityF ++ acrFilterF ++ fr")")
    }
  }

  def authorizedF(user: User,
                  objectType: ObjectType,
                  actionType: ActionType): Option[Fragment] =
    user.isSuperuser match {
      case true =>
        Some(fr"true")
      case false =>
        queryObjectsF(user, objectType, actionType)
    }
}
