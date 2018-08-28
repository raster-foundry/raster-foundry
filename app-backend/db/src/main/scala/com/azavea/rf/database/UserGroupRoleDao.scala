package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.filter.Filters._
import com.azavea.rf.database.notification.{GroupNotifier, MessageType}
import com.azavea.rf.database.notification.templates.PlainGroupRequest
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest
import io.circe._

import java.util.UUID

object UserGroupRoleDao extends Dao[UserGroupRole] {
  val tableName = "user_group_roles"

  val selectF =
    fr"""
      SELECT
        id, created_at, created_by,
        modified_at, modified_by, is_active,
        user_id, group_type, group_id, group_role, membership_status
      FROM
    """ ++ tableF

  def createF(ugr: UserGroupRole) =
    fr"INSERT INTO" ++ tableF ++ fr"""(
        id, created_at, created_by,
        modified_at, modified_by, is_active,
        user_id, group_type, group_id, group_role, membership_status
      ) VALUES (
          ${ugr.id}, ${ugr.createdAt}, ${ugr.createdBy},
          ${ugr.modifiedAt}, ${ugr.modifiedBy}, ${ugr.isActive},
          ${ugr.userId}, ${ugr.groupType}, ${ugr.groupId}, ${ugr.groupRole},
          ${ugr.membershipStatus} :: membership_status
      )
    """

  // We don't want to support changes to roles beyond deactivation and promotion
  def updateF(ugr: UserGroupRole, id: UUID, user: User) =
    fr"UPDATE" ++ tableF ++ fr"""SET
          modified_at = NOW(),
          modified_by = ${user.id},
          is_active = ${ugr.isActive},
          group_role = ${ugr.groupRole}
          where id = ${id}
        """

  def getUserGroupRoleById(ugrId: UUID,
                           user: User): ConnectionIO[Option[UserGroupRole]] =
    query.filter(ugrId).filter(fr"user_id = ${user.id}").selectOption

  def unsafeGetUserGroupRoleById(ugrId: UUID,
                                 user: User): ConnectionIO[UserGroupRole] =
    query.filter(ugrId).filter(fr"user_id = ${user.id}").select

  def create(ugr: UserGroupRole): ConnectionIO[UserGroupRole] = {
    val isValidGroupIO = ugr.groupType match {
      case GroupType.Platform => PlatformDao.query.filter(ugr.groupId).exists
      case GroupType.Organization =>
        OrganizationDao.query.filter(ugr.groupId).exists
      case GroupType.Team => TeamDao.query.filter(ugr.groupId).exists
    }

    val create = createF(ugr).update.withUniqueGeneratedKeys[UserGroupRole](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "is_active",
      "user_id",
      "group_type",
      "group_id",
      "group_role",
      "membership_status"
    )

    for {
      isValid <- isValidGroupIO
      createUGR <- {
        if (isValid) create
        else
          throw new Exception(
            s"Invalid group: ${ugr.groupType}(${ugr.groupId})")
      }
    } yield createUGR
  }

  @SuppressWarnings(Array("OptionGet"))
  def createWithGuard(adminCheckFunc: (User, UUID) => ConnectionIO[Boolean],
                      groupType: GroupType)(
      groupId: UUID,
      actingUser: User,
      subjectId: String,
      userGroupRoleCreate: UserGroupRole.Create,
      platformId: UUID,
      isSameOrgIO: ConnectionIO[Boolean]): ConnectionIO[UserGroupRole] =
    for {
      adminCheck <- adminCheckFunc(actingUser, groupId)
      existingRoleO <- {
        query
          .filter(fr"user_id = ${subjectId}")
          .filter(fr"group_id = ${groupId}")
          .filter(fr"group_type = ${groupType.toString} :: group_type")
          .filter(fr"is_active = true")
          .selectOption
      }
      roleTargetEmail <- UserDao.unsafeGetUserById(subjectId) map { _.email }
      roleCreatorEmail <- UserDao.unsafeGetUserById(actingUser.id) map {
        _.email
      }
      existingMembershipStatus = existingRoleO map { _.membershipStatus }
      rolesMatch = existingRoleO
        .map(
          (ugr: UserGroupRole) => ugr.groupRole == userGroupRoleCreate.groupRole
        )
        .getOrElse(false)
      isSameOrg <- isSameOrgIO
      createdOrReturned <- {
        (existingMembershipStatus, adminCheck, rolesMatch) match {
          // Only admins can change group roles, and only approved roles can have their group role changed
          case (Some(MembershipStatus.Approved), true, false) =>
            UserGroupRoleDao.deactivate(existingRoleO.map(_.id).get, actingUser) *>
              UserGroupRoleDao.create(
                userGroupRoleCreate.toUserGroupRole(actingUser,
                                                    MembershipStatus.Approved))
          // Accepting a role requires agreement about what the groupRole should be -- users can't cheat
          // and become admins by accepting a MEMBER role by posting an ADMIN role
          case (Some(MembershipStatus.Requested), true, true) |
              (Some(MembershipStatus.Invited), _, true) =>
            UserGroupRoleDao.deactivate(existingRoleO.map(_.id).get, actingUser) *>
              UserGroupRoleDao.create(
                userGroupRoleCreate.toUserGroupRole(actingUser,
                                                    MembershipStatus.Approved))
          // rolesMatch will always be false when existingRoleO is None, so don't bother checking it
          case (None, true, _) =>
            if (isSameOrg) {
              UserGroupRoleDao.create(
                userGroupRoleCreate.toUserGroupRole(actingUser,
                                                    MembershipStatus.Approved))
            } else {
              UserGroupRoleDao.create(
                userGroupRoleCreate
                  .toUserGroupRole(actingUser, MembershipStatus.Invited)) <*
                GroupNotifier(
                  platformId,
                  groupId,
                  groupType,
                  actingUser.id,
                  subjectId,
                  MessageType.GroupInvitation
                ).send
            }
          case (None, false, _) => {
            UserGroupRoleDao.create(
              userGroupRoleCreate
                .toUserGroupRole(actingUser, MembershipStatus.Requested)) <*
              GroupNotifier(
                platformId,
                groupId,
                groupType,
                subjectId,
                subjectId,
                MessageType.GroupRequest
              ).send
          }
          case (Some(_), _, _) => existingRoleO.get.pure[ConnectionIO]
        }
      }
    } yield { createdOrReturned }

  def getOption(id: UUID): ConnectionIO[Option[UserGroupRole]] = {
    query.filter(id).selectOption
  }

  // List roles that a given user has been granted
  def listByUser(user: User): ConnectionIO[List[UserGroupRole]] = {
    query
      .filter(fr"user_id = ${user.id}")
      .filter(fr"is_active = true")
      .list
  }

  def listByUserAndGroupType(
      user: User,
      groupType: GroupType): ConnectionIO[List[UserGroupRole]] = {
    query
      .filter(fr"user_id = ${user.id}")
      .filter(fr"group_type = ${groupType}")
      .list
  }

  def listByGroupQ(groupType: GroupType, groupId: UUID) =
    query
      .filter(fr"group_type = ${groupType}")
      .filter(fr"group_id = ${groupId}")
      .filter(fr"is_active = true")

  // List roles that have been given to users for a group
  def listByGroup(groupType: GroupType,
                  groupId: UUID): ConnectionIO[List[UserGroupRole]] =
    listByGroupQ(groupType, groupId).list

  // List roles that have been given to users for a group of a specific type
  def listByGroupAndRole(
      groupType: GroupType,
      groupId: UUID,
      groupRole: GroupRole): ConnectionIO[List[UserGroupRole]] = {
    listByGroupQ(groupType, groupId)
      .filter(fr"group_role = ${groupRole.toString} :: group_role")
      .list
  }

  // List a user's roles in a group
  def listUserGroupRoles(groupType: GroupType,
                         groupId: UUID,
                         userId: String): ConnectionIO[List[UserGroupRole]] = {
    query
      .filter(fr"group_type = ${groupType}")
      .filter(fr"group_id = ${groupId}")
      .filter(fr"user_id = ${userId}")
      .filter(fr"is_active = true")
      .list
  }

  def listUsersByGroup(groupType: GroupType,
                       groupId: UUID,
                       page: PageRequest,
                       searchParams: SearchQueryParameters,
                       actingUser: User,
                       orderClauseO: Option[Fragment] = None)
    : ConnectionIO[PaginatedResponse[User.WithGroupRole]] = {
    // PUBLIC users can be seen by anyone within the same platform
    // PRIVATE users can be seen by anyone within the same organization
    // PRIVATE users can be seen by anyone within the same team (even if orgs are different)
    val sf =
      fr"""
        SELECT u.id, u.role, u.created_at, u.modified_at,
          u.dropbox_credential, u.planet_credential, u.email_notifications,
          u.email, u.name, u.profile_image_uri, u.is_superuser, u.is_active, u.visibility,
          ugr.group_role, ugr.membership_status
        FROM """ ++ tableF ++ fr""" ugr
        JOIN """ ++ UserDao.tableF ++ fr""" u
          ON u.id = ugr.user_id
      """

    val cf =
      fr"""
        SELECT count(ugr.id)
        FROM """ ++ tableF ++ fr"""AS ugr
        JOIN """ ++ UserDao.tableF ++ fr""" u
          ON u.id = ugr.user_id
      """

    // Filter that applies the following rules:
    // 1) A user is always allowed to list themselves
    // 2) A public user is always visible
    // 3) A private user is only visible to those within the same organization or team

    val ff =
      fr"""(
        u.id = ${actingUser.id} OR
        u.visibility = ${UserVisibility.Public.toString}::user_visibility OR
        u.id IN (
          SELECT A.user_id
          FROM """ ++ tableF ++ fr""" A
          JOIN """ ++ tableF ++ fr""" B
            ON A.group_type = B.group_type AND
              A.group_id = B.group_id
          WHERE
            B.user_id = ${actingUser.id} AND
            (
              B.group_type = ${GroupType.Organization.toString}::group_type OR
              B.group_type = ${GroupType.Team.toString}::group_type
            )
        )
      )"""

    for {
      userIsPlatformAdmin <- {
        UserGroupRoleDao.query
          .filter(fr"user_id = ${actingUser.id}")
          .filter(fr"group_type = ${GroupType.Platform.toString}::group_type")
          .filter(fr"group_role = ${GroupRole.Admin.toString}::group_role")
          .filter(fr"is_active = true")
          .exists
      }
      isSuperUser <- actingUser.isSuperuser.pure[ConnectionIO]
      result <- {
        query
          .filter(fr"ugr.group_type = ${groupType}")
          .filter(fr"ugr.group_id = ${groupId}")
          .filter(fr"ugr.is_active = true")
          .filter(searchQP(searchParams, List("u.name", "u.email", "u.id")))
          .filter(
            (userIsPlatformAdmin || isSuperUser) match {
              case true  => None
              case false => Some(ff)
            }
          )
          .page[User.WithGroupRole](page, sf, cf, orderClauseO match {
            case Some(orderClause) => orderClause
            case None              => fr""
          })
      }
    } yield { result }
  }

  // @TODO: ensure a user cannot demote (or promote?) themselves
  def update(ugr: UserGroupRole, id: UUID, user: User): ConnectionIO[Int] =
    updateF(ugr, id, user).update.run

  def deactivate(id: UUID, user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr""" SET
          is_active = false,
          modified_at = NOW(),
          modified_by = ${user.id}
            WHERE id = ${id}
        """).update.run
  }

  def deactivateUserGroupRoles(
      ugr: UserGroupRole.UserGroup,
      user: User): ConnectionIO[List[UserGroupRole]] = {
    (fr"UPDATE" ++ tableF ++ fr"""SET
        modified_at = NOW(),
        modified_by = ${user.id},
        is_active = false
        """ ++ Fragments.whereAnd(
      fr"user_id = ${ugr.userId}",
      fr"group_type = ${ugr.groupType}",
      fr"group_id = ${ugr.groupId}",
      fr"is_active = true"
    )).update
      .withGeneratedKeys[UserGroupRole](
        "id",
        "created_at",
        "created_by",
        "modified_at",
        "modified_by",
        "is_active",
        "user_id",
        "group_type",
        "group_id",
        "group_role",
        "membership_status"
      )
      .compile
      .toList
  }

  def deactivateByGroup(groupType: GroupType, groupId: UUID) = {
    (fr"UPDATE" ++ tableF ++ fr"""SET
        is_active = false
        """ ++ Fragments.whereAnd(fr"group_type = ${groupType}",
                                  fr"group_id = ${groupId}")).update.run
  }
}
