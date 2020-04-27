package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.ApplicativeError
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

trait ObjectPermissions[Model] {
  def tableName: String

  def authQuery(
      user: User,
      objectType: ObjectType,
      ownershipTypeO: Option[String] = None,
      groupTypeO: Option[GroupType] = None,
      groupIdO: Option[UUID] = None
  ): Dao.QueryBuilder[Model]

  def authorized(
      user: User,
      objectType: ObjectType,
      objectId: UUID,
      actionType: ActionType
  ): ConnectionIO[AuthResult[Model]]

  def isValidObject(id: UUID): ConnectionIO[Boolean] =
    (tableName match {
      case "campaigns"           => CampaignDao
      case "annotation_projects" => AnnotationProjectDao
      case "projects"            => ProjectDao
      case "scenes"              => SceneDao
      case "datasources"         => DatasourceDao
      case "shapes"              => ShapeDao
      case "tool_runs"           => ToolRunDao
      case "tools"               => ToolDao
      case "workspaces" | "templates" | "analyses" =>
        throw new Exception(s"${tableName} not yet supported")
    }).query.filter(id).exists

  def isValidPermission(
      acr: ObjectAccessControlRule,
      user: User
  ): ConnectionIO[Boolean] =
    (acr.subjectType, acr.subjectId, user) match {
      case (SubjectType.All, _, u) => u.isSuperuser.pure[ConnectionIO]
      case (SubjectType.Platform, Some(subjectId), user) =>
        PlatformDao.userIsAdmin(user, UUID.fromString(subjectId))
      case (SubjectType.Organization, Some(subjectId), _) =>
        OrganizationDao.query.filter(UUID.fromString(subjectId)).exists
      case (SubjectType.Team, Some(subjectId), _) =>
        TeamDao.query.filter(UUID.fromString(subjectId)).exists
      case (SubjectType.User, Some(subjectId), _) =>
        UserDao.filterById(subjectId).exists
      case _ =>
        false.pure[ConnectionIO]
    }

  def getPermissionsF(id: UUID): Fragment =
    Fragment.const(s"SELECT acrs FROM ${tableName}") ++ Fragments.whereAndOpt(
      Some(fr"id = ${id}")
    )

  def appendPermissionF(id: UUID, acr: ObjectAccessControlRule): Fragment =
    Fragment.const(s"""
    UPDATE ${tableName}
    SET acrs = array_append(acrs, '${acr.toObjAcrString}'::text)
  """) ++ Fragments.whereAndOpt((Some(fr"id = ${id}")))

  def updatePermissionsF(
      id: UUID,
      acrList: List[ObjectAccessControlRule],
      replace: Boolean = false
  ): Fragment = {
    val newAcrs: String = acrList match {
      case Nil => "'{}'::text[]"
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
      s"SELECT a.acrs from (SELECT UNNEST(acrs) acrs from ${tableName}"
    ) ++
      Fragments.whereAndOpt(Some(fr"id=${id}")) ++ Fragment.const(") a") ++
      Fragment.const(
        s"WHERE a.acrs LIKE '%${user.id}%' OR a.acrs LIKE '%ALL%' OR ${groupIdsF}"
      )

  def acrStringsToList(
      acrs: List[String]
  ): List[ObjectAccessControlRule] =
    acrs.flatMap(ObjectAccessControlRule.fromObjAcrString)

  def getPermissions(
      id: UUID
  ): ConnectionIO[List[ObjectAccessControlRule]] =
    isValidObject(id) flatMap {
      case false => throw new Exception(s"Invalid ${tableName} object ${id}")
      case true =>
        getPermissionsF(id)
          .query[List[String]]
          .unique
          .map(acrStringsToList(_))

    }

  def addPermission(
      id: UUID,
      acr: ObjectAccessControlRule
  ): ConnectionIO[List[ObjectAccessControlRule]] =
    for {
      permissions <- getPermissions(id)
      permExists = permissions.contains(acr)
      addPermission <- permExists match {
        case true =>
          // if the permission already exists, return the list of permissions.
          // a client's expressed intent -- perm a should be included for this object --
          // is satisfied, so don't make them care about prior states of the world
          permissions.pure[ConnectionIO]
        case false =>
          appendPermissionF(id, acr).update
            .withUniqueGeneratedKeys[List[String]]("acrs")
            .map(acrStringsToList(_))
      }
    } yield { addPermission }

  def addPermissionsMany(
      id: UUID,
      acrList: List[ObjectAccessControlRule],
      replace: Boolean = false
  ): ConnectionIO[List[ObjectAccessControlRule]] = {
    for {
      addPermissionsMany <- acrList match {
        case Nil if !replace =>
          ApplicativeError[ConnectionIO, Throwable].raiseError(
            new Exception(s"All permissions exist for ${tableName} ${id}")
          )
        case Nil if replace =>
          ApplicativeError[ConnectionIO, Throwable].raiseError(
            new Exception(
              "Cannot replace permissions with empty permission list"
            )
          )
        case _ =>
          updatePermissionsF(id, acrList, replace).update
            .withUniqueGeneratedKeys[List[String]]("acrs")
            .map(acrStringsToList(_))
      }
    } yield { addPermissionsMany }
  }

  def replacePermissions(
      id: UUID,
      acrList: List[ObjectAccessControlRule]
  ): ConnectionIO[Either[Throwable, List[ObjectAccessControlRule]]] =
    addPermissionsMany(id, acrList, true).attempt

  def deletePermissions(id: UUID): ConnectionIO[Int] =
    updatePermissionsF(id, List[ObjectAccessControlRule]()).update.run

  def listUserActions(user: User, id: UUID): ConnectionIO[List[String]] =
    for {
      ugrs <- UserGroupRoleDao.listByUser(user)
      groupIdString = ugrs
        .map((ugr: UserGroupRole) => s"a.acrs LIKE '%${ugr.groupId.toString}%'")
        .mkString(" OR ")
      listUserActions <- listUserActionsF(user, id, groupIdString)
        .query[String]
        .to[List]
      actions = acrStringsToList(listUserActions)
        .map(_.actionType.toString)
        .distinct
    } yield { actions }

  // TODO: in card #4020
  // def deactivateBySubject(subjectType: SubjectType, subjectId: String)

  def createVisibilityF(
      objectType: ObjectType,
      actionType: ActionType,
      tableName: String
  ): Fragment =
    (objectType, actionType) match {
      case (ObjectType.Shape, ActionType.View) |
          (ObjectType.AnnotationProject, ActionType.View) |
          (ObjectType.Campaign, ActionType.View) =>
        Fragment.empty
      case (_, ActionType.View) | (ObjectType.Scene, ActionType.Download) |
          (ObjectType.Project, ActionType.Export) |
          (ObjectType.Project, ActionType.Annotate) |
          (ObjectType.Analysis, ActionType.Export) =>
        Fragment.const(s"${tableName}visibility = 'PUBLIC' OR")
      case _ =>
        Fragment.empty
    }

  def createInheritedF(
      user: User,
      actionType: ActionType,
      groupTypeO: Option[GroupType],
      groupIdO: Option[UUID]
  ): Fragment =
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

  def queryObjectsF(
      user: User,
      objectType: ObjectType,
      actionType: ActionType,
      ownershipTypeO: Option[String] = None,
      groupTypeO: Option[GroupType] = None,
      groupIdO: Option[UUID] = None,
      tableNameO: Option[String] = None
  ): Option[Fragment] = {
    val tableName: String = tableNameO match {
      case Some(tableName) => s"${tableName}."
      case _               => ""
    }
    val ownedF: Fragment =
      Fragment.const(s"${tableName}owner =") ++ fr"${user.id}"
    val visibilityF: Fragment =
      createVisibilityF(objectType, actionType, tableName)
    val sharedF: Fragment =
      Fragment.const(
        s"""ARRAY['ALL;;${actionType.toString}', 'USER;${user.id};${actionType.toString}']"""
      )
    val inheritedF: Fragment =
      createInheritedF(user, actionType, groupTypeO, groupIdO)
    val acrFilterF
        : Fragment = fr"array_cat(" ++ sharedF ++ fr"," ++ inheritedF ++ fr") &&" ++ Fragment
      .const(s"${tableName}acrs")

    ownershipTypeO match {
      // owned by the requesting user only
      case Some("owned") =>
        Some(ownedF)
      // shared to the requesting user directly, across platform, or due to group membership
      case Some("shared") =>
        val scenePublicExclude: Option[Fragment] =
          if (objectType == ObjectType.Scene) {
            Some(
              Fragment
                .const(s"(${tableName}visibility)") ++ fr" != 'PUBLIC' AND "
            )
          } else {
            None
          }
        if (objectType == ObjectType.Shape || objectType == ObjectType.Template) {
          Some(
            fr"(" ++ acrFilterF ++ fr") AND" ++ Fragment
              .const(s"${tableName}owner") ++ fr"<> ${user.id}"
          )
        } else {
          scenePublicExclude combine Some(
            acrFilterF ++ fr" AND " ++ Fragment
              .const(s"${tableName}owner") ++ fr"<> ${user.id}"
          )
        }
      // shared to the requesting user due to group membership
      case Some("inherited") =>
        if (objectType == ObjectType.Shape || objectType == ObjectType.AnnotationProject || objectType == ObjectType.Campaign) {
          Some(inheritedF ++ Fragment.const(s"&& ${tableName}acrs"))
        } else {
          Some(
            Fragment
              .const(s"${tableName}visibility") ++ fr"!= 'PUBLIC' AND (" ++ inheritedF ++ Fragment
              .const(s"&& ${tableName}acrs)")
          )
        }
      // the default
      case _ =>
        Some(fr"(" ++ ownedF ++ fr"OR" ++ visibilityF ++ acrFilterF ++ fr")")
    }
  }

  def authorizedF(
      user: User,
      objectType: ObjectType,
      actionType: ActionType
  ): Option[Fragment] =
    user.isSuperuser match {
      case true =>
        Some(fr"true")
      case false =>
        queryObjectsF(user, objectType, actionType)
    }

  def isReplaceWithinScopedLimit(
      domain: Domain,
      user: User,
      acrList: List[ObjectAccessControlRule]
  ): Boolean =
    (Scopes.resolveFor(
      domain,
      Action.Share,
      user.scope.actions
    ) map { action =>
      (action.limit map { limit =>
        val distinctUsers = acrList.foldLeft(Set.empty[String])({
          case (
              accum: Set[String],
              ObjectAccessControlRule(SubjectType.User, Some(subjId), _)
              ) =>
            accum | Set(subjId)
          case (accum: Set[String], _) => accum
        })
        distinctUsers.size.toLong <= limit
      }) getOrElse { true } // if there's no limit, then the limit is infinity, so this action is allowed
    }) getOrElse { false } // if there's no scope, then the action is not allowed

  def getShareCount(id: UUID, userId: String): ConnectionIO[Long] =
    getPermissions(id)
      .map { acrList =>
        acrList
          .foldLeft(Set.empty[String])(
            (accum: Set[String], acr: ObjectAccessControlRule) => {
              acr match {
                case ObjectAccessControlRule(
                    SubjectType.User,
                    Some(subjectId),
                    _
                    ) if subjectId != userId =>
                  Set(subjectId) | accum
                case _ => accum
              }
            }
          )
          .size
          .toLong
      }
}
