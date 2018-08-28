package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.{
  User,
  UserRole,
  Credential,
  UserVisibility,
  OrganizationType
}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

import scala.concurrent.Future

import com.azavea.rf.database.Implicits._

object UserDao extends Dao[User] {

  val tableName = "users"

  val selectF = sql"""
    SELECT
      id, role, created_at, modified_at,
      dropbox_credential, planet_credential, email_notifications,
      email, name, profile_image_uri, is_superuser, is_active, visibility, personal_info
    FROM
  """ ++ tableF

  def filterById(id: String) = {
    query.filter(fr"id = ${id}")
  }

  def unsafeGetUserById(
      id: String,
      isOwn: Option[Boolean] = Some(true)): ConnectionIO[User] = isOwn match {
    case Some(true) => filterById(id).select
    case _ =>
      filterById(id).select map { (user: User) =>
        {
          user.copy(planetCredential = Credential(Some("")),
                    dropboxCredential = Credential(Some("")))
        }
      }
  }

  def unsafeGetUserPlatform(id: String): ConnectionIO[Platform] =
    for {
      platformRole <- {
        UserGroupRoleDao.query
          .filter(fr"group_type = 'PLATFORM' :: group_type")
          .filter(fr"user_id = $id")
          .filter(fr"is_active = true")
          .select
      }
      platform <- PlatformDao.unsafeGetPlatformById(platformRole.groupId)
    } yield platform

  def getUserById(id: String): ConnectionIO[Option[User]] = {
    filterById(id).selectOption
  }

  def getUsersByIds(ids: List[String]): ConnectionIO[List[User]] = {
    ids.toNel match {
      case Some(idsNel) =>
        query.filter(Fragments.in(fr"id", idsNel)).list
      case None =>
        List.empty[User].pure[ConnectionIO]
    }
  }

  def getUserAndActiveRolesById(
      id: String): ConnectionIO[UserOptionAndRoles] = {
    for {
      user <- getUserById(id)
      roles <- {
        user match {
          case Some(u) =>
            UserGroupRoleDao.listByUser(u)
          case _ =>
            List.empty[UserGroupRole].pure[ConnectionIO]
        }
      }
    } yield UserOptionAndRoles(user, roles)
  }

  def createUserWithJWT(
      creatingUser: User,
      jwtUser: User.JwtFields): ConnectionIO[(User, List[UserGroupRole])] = {
    for {
      organization <- OrganizationDao.query
        .filter(jwtUser.organizationId)
        .selectOption
      createdUser <- {
        organization match {
          case Some(o) =>
            val newUser = User.Create(
              jwtUser.id,
              Viewer,
              jwtUser.email,
              jwtUser.name,
              jwtUser.picture
            )
            create(newUser)
          case None =>
            throw new RuntimeException(
              s"Tried to create a user using a non-existent organization ID: ${jwtUser.organizationId}"
            )
        }
      }
      platformRole <- UserGroupRoleDao.create(
        UserGroupRole
          .Create(
            createdUser.id,
            GroupType.Platform,
            jwtUser.platformId,
            GroupRole.Member
          )
          .toUserGroupRole(creatingUser, MembershipStatus.Approved)
      )
      organizationRole <- UserGroupRoleDao.create(
        UserGroupRole
          .Create(
            createdUser.id,
            GroupType.Organization,
            organization
              .getOrElse(
                throw new RuntimeException(
                  "Tried to create a user role using a non-existent organization ID")
              )
              .id,
            GroupRole.Member
          )
          .toUserGroupRole(creatingUser, MembershipStatus.Approved)
      )
    } yield (createdUser, List(platformRole, organizationRole))
  }

  def updateUser(user: User, userId: String): ConnectionIO[Int] = {

    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${userId}"

    (sql"""
       UPDATE users
       SET
         modified_at = ${updateTime},
         dropbox_credential = ${user.dropboxCredential.token.getOrElse("")},
         planet_credential = ${user.planetCredential.token.getOrElse("")},
         email_notifications = ${user.emailNotifications},
         email = ${user.email},
         name = ${user.name},
         profile_image_uri = ${user.profileImageUri},
         visibility = ${user.visibility}
       """ ++ Fragments.whereAndOpt(Some(idFilter))).update.run
  }

  def storeDropboxAccessToken(userId: String,
                              accessToken: Credential): ConnectionIO[Int] = {
    sql"""UPDATE users
          SET dropbox_credential = ${accessToken}
          WHERE id = ${userId}
    """.update.run
  }

  def create(newUser: User.Create): ConnectionIO[User] = {
    val now = new Timestamp(new java.util.Date().getTime())

    sql"""
       INSERT INTO users
          (id, role, created_at, modified_at, email_notifications,
          email, name, profile_image_uri, is_superuser, is_active, visibility)
       VALUES
          (${newUser.id}, ${UserRole.toString(newUser.role)}, ${now}, ${now}, false,
          ${newUser.email}, ${newUser.name}, ${newUser.profileImageUri}, false, true, ${UserVisibility.Private.toString}::user_visibility)
       """.update.withUniqueGeneratedKeys[User](
      "id",
      "role",
      "created_at",
      "modified_at",
      "dropbox_credential",
      "planet_credential",
      "email_notifications",
      "email",
      "name",
      "profile_image_uri",
      "is_superuser",
      "is_active",
      "visibility",
      "personal_info"
    )
  }

  def isSuperUserF(user: User) = fr"""
    SELECT count(id) > 0
      FROM """ ++ UserDao.tableF ++ fr"""
      WHERE
        id = ${user.id} AND
        is_superuser = true AND
        is_active = true
  """

  def isSuperUser(user: User): ConnectionIO[Boolean] =
    isSuperUserF(user).query[Boolean].option.map(_.getOrElse(false))

  def viewFilter(user: User): Dao.QueryBuilder[User] =
    Dao.QueryBuilder[User](
      user.isSuperuser match {
        case true => selectF
        case _ =>
          (selectF ++ fr"""
        JOIN (
          -- users in same platform and is admin
          SELECT target_ugr1.user_id AS user_id
          FROM user_group_roles requesting_ugr1
          JOIN user_group_roles target_ugr1 ON requesting_ugr1.group_id = target_ugr1.group_id
          WHERE
          requesting_ugr1.group_type = 'PLATFORM' AND
          requesting_ugr1.user_id = ${user.id} AND
          requesting_ugr1.group_role = 'ADMIN'

          UNION
          -- users in same platform and public
          SELECT target_ugr2.user_id AS user_id
          FROM user_group_roles requesting_ugr2
          JOIN user_group_roles target_ugr2 ON requesting_ugr2.group_id = target_ugr2.group_id
          JOIN users platform_users ON target_ugr2.user_id = platform_users.id
          WHERE
          requesting_ugr2.group_type = 'PLATFORM' AND
          requesting_ugr2.user_id = ${user.id} AND
          platform_users.visibility = 'PUBLIC'

          UNION
          -- users in same orgs
          SELECT target_ugr3.user_id AS user_id
          FROM user_group_roles requesting_ugr3
          JOIN user_group_roles target_ugr3 ON requesting_ugr3.group_id = target_ugr3.group_id
          JOIN users organization_users ON target_ugr3.user_id = organization_users.id
          WHERE
          requesting_ugr3.group_type = 'ORGANIZATION' AND
          requesting_ugr3.user_id = ${user.id}

          UNION
          -- users in same teams
          SELECT target_ugr4.user_id AS user_id
          FROM user_group_roles requesting_ugr4
          JOIN user_group_roles target_ugr4 ON requesting_ugr4.group_id = target_ugr4.group_id
          JOIN users team_users ON target_ugr4.user_id = team_users.id
          WHERE
          requesting_ugr4.group_type = 'TEAM' AND
          requesting_ugr4.user_id = ${user.id}
        ) AS search ON""" ++ Fragment
            .const(s"${tableName}.id") ++ fr"= search.user_id")
      },
      tableF,
      List.empty
    )

  def searchUsers(
      user: User,
      searchParams: SearchQueryParameters): ConnectionIO[List[User]] = {
    UserDao
      .viewFilter(user)
      .filter(searchParams)
      .list(0, 5, fr"order by name")
  }

  def updateOwnUser(user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    (
      sql"""
        UPDATE users
        SET
          modified_at = ${updateTime},
          planet_credential = ${user.planetCredential.token.getOrElse("")},
          email_notifications = ${user.emailNotifications},
          visibility = ${user.visibility},
          personal_info = ${user.personalInfo}
          """ ++
        Fragments.whereAndOpt(Some(fr"id = ${user.id}"))
    ).update.run
  }
}
