package com.azavea.rf.database

import java.util.UUID

import cats.free.Free
import com.azavea.rf.database.filter.Filters._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import doobie.free.connection
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.{Fragment, Fragments, _}

object PlatformDao extends Dao[Platform] {

  val tableName = "platforms"

  val selectF: Fragment = sql"""
    SELECT
      id, name, public_settings, is_active, default_organization_id, private_settings
    FROM
  """ ++ tableF

  def createF(platform: Platform): Fragment =
    fr"INSERT INTO" ++ tableF ++ fr"""(
        id, name, public_settings , is_active, default_organization_id, private_settings
      )
      VALUES (
        ${platform.id}, ${platform.name}, ${platform.publicSettings}, ${platform.isActive},
        ${platform.defaultOrganizationId}, ${platform.privateSettings}
      )
  """

  def getPlatformById(platformId: UUID): ConnectionIO[Option[Platform]] =
    query.filter(platformId).selectOption

  def unsafeGetPlatformById(platformId: UUID): ConnectionIO[Platform] =
    query.filter(platformId).select

  def listPlatforms(
      page: PageRequest): ConnectionIO[PaginatedResponse[Platform]] =
    query.page(page, fr"")

  def listMembers(
      platformId: UUID,
      page: PageRequest,
      searchParams: SearchQueryParameters,
      actingUser: User): ConnectionIO[PaginatedResponse[User.WithGroupRole]] = {
    for {
      isAdmin <- userIsAdmin(actingUser, platformId)
      userPageIO <- {
        val userListPage = UserGroupRoleDao.listUsersByGroup(
          GroupType.Platform,
          platformId,
          page,
          searchParams,
          actingUser,
          Some(fr"ORDER BY ugr.membership_status, ugr.group_role"))
        if (isAdmin) {
          userListPage
        } else {
          userListPage.map { usersPage: PaginatedResponse[User.WithGroupRole] =>
            {
              usersPage.copy(results = usersPage.results map {
                _.copy(email = "")
              })
            }
          }
        }
      }
    } yield userPageIO
  }

  def listPlatformUserTeams(
      user: User,
      searchParams: SearchQueryParameters): ConnectionIO[List[Team]] = {
    val teamsF: Option[Fragment] = Some(fr"""
      id IN (
        SELECT group_id
        FROM user_group_roles
        WHERE
          group_type = 'TEAM' AND
          user_id = ${user.id} AND
          is_active = true
        )
    """)
    val organizationsF: Option[Fragment] = Some(fr"""
      organization_id IN (
        SELECT group_id
        FROM user_group_roles
        WHERE
          group_type = 'ORGANIZATION' AND
          user_id = ${user.id} AND
          is_active = true
        )
    """)
    TeamDao.query
      .filter(Fragment.const("(") ++ Fragments
        .orOpt(teamsF, organizationsF) ++ Fragment.const(")"))
      .filter(searchQP(searchParams, List("name")))
      .filter(fr"is_active = true")
      .list(0, 5, fr"order by name")
  }

  def create(platform: Platform): ConnectionIO[Platform] = {
    createF(platform).update.withUniqueGeneratedKeys[Platform](
      "id",
      "name",
      "public_settings",
      "is_active",
      "default_organization_id",
      "private_settings"
    )
  }

  def update(platform: Platform, id: UUID): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"""SET
        name = ${platform.name},
        public_settings = ${platform.publicSettings},
        default_organization_id = ${platform.defaultOrganizationId},
        private_settings = ${platform.privateSettings}
        where id = ${id}
      """).update.run
  }

  def validatePath(platformId: UUID): ConnectionIO[Boolean] =
    (fr"""
    SELECT count(p.id) > 0
    FROM """ ++ tableF ++ fr""" p
    WHERE p.id = ${platformId}
  """).query[Boolean].option.map(_.getOrElse(false))

  def userIsMemberF(user: User, platformId: UUID): Fragment = fr"""
    SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
        SELECT count(id) > 0
        FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
        WHERE
          user_id = ${user.id} AND
          group_type = ${GroupType.Platform.toString}::group_type AND
          group_id = ${platformId} AND
          is_active = true
      )
  """

  def userIsMember(user: User, platformId: UUID): ConnectionIO[Boolean] =
    userIsMemberF(user, platformId)
      .query[Boolean]
      .option
      .map(_.getOrElse(false))

  def userIsAdminF(user: User, platformId: UUID): Fragment = fr"""
      SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
        SELECT count(ugr.id) > 0
        FROM """ ++ UserGroupRoleDao.tableF ++ fr""" ugr
        JOIN """ ++ tableF ++ fr""" p ON ugr.group_id = p.id
        WHERE
          ugr.user_id = ${user.id} AND
          ugr.group_type = ${GroupType.Platform.toString}::group_type AND
          ugr.group_role = ${GroupRole.Admin.toString}::group_role AND
          ugr.group_id = ${platformId} AND
          ugr.is_active = true AND
          p.is_active = true AND
          membership_status = 'APPROVED'
      )
  """

  def userIsAdmin(user: User,
                  platformId: UUID): Free[connection.ConnectionOp, Boolean] =
    userIsAdminF(user, platformId).query[Boolean].option.map(_.getOrElse(false))

  def delete(platformId: UUID): ConnectionIO[Int] =
    PlatformDao.query.filter(platformId).delete

  def addUserRole(actingUser: User,
                  subjectId: String,
                  platformId: UUID,
                  userRole: GroupRole): ConnectionIO[UserGroupRole] = {
    val userGroupRoleCreate = UserGroupRole.Create(
      subjectId,
      GroupType.Platform,
      platformId,
      userRole
    )
    UserGroupRoleDao.create(
      userGroupRoleCreate.toUserGroupRole(actingUser,
                                          MembershipStatus.Approved))
  }

  def setUserRole(actingUser: User,
                  subjectId: String,
                  platformId: UUID,
                  userRole: GroupRole): ConnectionIO[List[UserGroupRole]] = {
    deactivateUserRoles(actingUser, subjectId, platformId)
      .flatMap(
        updatedRoles =>
          addUserRole(actingUser, subjectId, platformId, userRole)
            .map((ugr: UserGroupRole) => updatedRoles ++ List(ugr))
      )
  }

  def deactivateUserRoles(
      actingUser: User,
      subjectId: String,
      platformId: UUID): ConnectionIO[List[UserGroupRole]] = {
    val userGroup =
      UserGroupRole.UserGroup(subjectId, GroupType.Platform, platformId)
    UserGroupRoleDao.deactivateUserGroupRoles(userGroup, actingUser)
  }

  def activatePlatform(platformId: UUID): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"""SET
       is_active = true,
       where id = ${platformId}
      """).update.run
  }

  def deactivatePlatform(platformId: UUID): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr"""SET
       is_active = false,
       where id = ${platformId}
      """).update.run
  }

  def organizationIsPublicOrg(organizationId: UUID,
                              platformId: UUID): ConnectionIO[Boolean] = {
    query.filter(platformId).selectOption map { platformO =>
      {
        platformO.exists(_.defaultOrganizationId.contains(organizationId))
      }
    }
  }

  def getPlatUsersAndProjByConsumerAndSceneID(
      userIds: List[String],
      sceneId: UUID): ConnectionIO[List[PlatformWithUsersSceneProjects]] = {
    val userIdsString = "(" ++ userIds
      .map("'" ++ _ ++ "'")
      .mkString(", ") ++ ")"
    val sceneIdString = "'" ++ sceneId.toString ++ "'"
    Fragment.const(s"""
        SELECT plat.id AS plat_id, plat.name AS plat_name, u.id AS u_id, u.name AS u_name,
               plat.public_settings AS pub_settings, plat.private_settings AS pri_settings,
               u.email AS email, u.email_notifications AS email_notifications,
               prj.id AS projectId, prj.name AS project_name, u.personal_info AS personal_info
        FROM users AS u
          JOIN user_group_roles AS ugr
          ON u.id = ugr.user_id
          JOIN platforms AS plat
          ON ugr.group_id = plat.id
          JOIN projects AS prj
          ON u.id = prj.owner
          JOIN scenes_to_projects AS stp
          ON prj.id = stp.project_id
        WHERE stp.scene_id = ${sceneIdString}
          AND u.id IN ${userIdsString}
      """).query[PlatformWithUsersSceneProjects].to[List]
  }

  def getPlatAndUsersBySceneOwnerId(
      sceneOwnerId: String): ConnectionIO[PlatformWithSceneOwner] = {
    val ownerId = "'" ++ sceneOwnerId ++ "'"
    Fragment.const(s"""
        SELECT DISTINCT
          plat.id AS plat_id, plat.name AS plat_name, u.id AS u_id, u.name AS u_name,
          plat.public_settings AS pub_settings, plat.private_settings AS pri_settings,
          u.email AS email, u.email_notifications AS email_notifications,
          u.personal_info AS personal_info
        FROM users AS u
          JOIN user_group_roles AS ugr
          ON u.id = ugr.user_id
          JOIN platforms AS plat
          ON ugr.group_id = plat.id
        WHERE u.id = ${ownerId}
      """).query[PlatformWithSceneOwner].unique
  }
}
