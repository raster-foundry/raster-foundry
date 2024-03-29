package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.Cache
import com.rasterfoundry.database.util.Sanitization
import com.rasterfoundry.datamodel._

import cats.data._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import io.circe.syntax._
import scalacache.CatsEffect.modes._
import scalacache._

import scala.concurrent.duration._

import java.sql.Timestamp

object UserDao extends Dao[User] with Sanitization {

  val tableName = "users"

  import Cache.UserCache._
  import Cache.UserWithPlatformCache._

  override val fieldNames = List(
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
    "personal_info",
    "scopes"
  )

  val selectF = fr"SELECT" ++ selectFieldsF ++ fr"FROM" ++ tableF

  def filterById(id: String) = {
    query.filter(fr"id = ${id}")
  }

  def unsafeGetUserById(
      id: String,
      isOwn: Option[Boolean] = Some(true)
  ): ConnectionIO[User] =
    isOwn match {
      case Some(true) => filterById(id).select
      case _ =>
        filterById(id).select map { sanitizeUser _ }
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

  def getUserById(id: String): ConnectionIO[Option[User]] =
    Cache.getOptionCache(User.cacheKey(id), Some(30 minutes)) {
      filterById(id).selectOption
    }

  def getUserWithPlatformById(
      id: String
  ): ConnectionIO[Option[UserWithPlatform]] =
    Cache.getOptionCache(UserWithPlatform.cacheKey(id), Some(30 minutes)) {
      for {
        userOpt <- filterById(id).selectOption
        platformIdOpt <- userOpt flatTraverse { user =>
          UserGroupRoleDao.getUserMostRecentActivePlatform(user.id)
        }
        plaformOpt <- platformIdOpt flatTraverse {
          PlatformDao.getPlatformById(_)
        }
      } yield
        userOpt match {
          case Some(user) =>
            Some(
              UserWithPlatform(
                user.id,
                user.role,
                user.createdAt,
                user.modifiedAt,
                user.dropboxCredential,
                user.planetCredential,
                user.emailNotifications,
                user.email,
                user.name,
                user.profileImageUri,
                user.isSuperuser,
                user.isActive,
                user.visibility,
                user.personalInfo,
                user.scope,
                plaformOpt map { _.name },
                platformIdOpt
              )
            )
          case None => None
        }
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
      id: String
  ): ConnectionIO[UserOptionAndRoles] = {
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
      jwtUser: User.JwtFields,
      userRole: GroupRole,
      scope: Scope
  ): ConnectionIO[(User, List[UserGroupRole])] = {
    for {
      organization <- OrganizationDao.query
        .filter(jwtUser.organizationId)
        .selectOption
      createdUser <- {
        organization match {
          case Some(_) =>
            val newUser = User.Create(
              jwtUser.id,
              Viewer,
              jwtUser.email,
              jwtUser.name,
              jwtUser.picture,
              scope
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
            userRole
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
                  "Tried to create a user role using a non-existent organization ID"
                )
              )
              .id,
            userRole
          )
          .toUserGroupRole(creatingUser, MembershipStatus.Approved)
      )
    } yield (createdUser, List(platformRole, organizationRole))
  }

  def updateUser(user: User, userId: String): ConnectionIO[Int] = {

    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${userId}"
    val dropboxCredential = user.dropboxCredential.token.getOrElse("")
    val planetCredential = user.planetCredential.token.getOrElse("")

    for {
      query <- (sql"""
       UPDATE users
       SET
         modified_at = ${updateTime},
         dropbox_credential = ${dropboxCredential},
         planet_credential = ${planetCredential},
         email_notifications = ${user.emailNotifications},
         email = ${user.email},
         name = ${user.name},
         profile_image_uri = ${user.profileImageUri},
         visibility = ${user.visibility},
         personal_info = ${user.personalInfo}
        """ ++ Fragments.whereAndOpt(Some(idFilter))).update.run
      _ <- remove(user.cacheKey)(userCache, async[ConnectionIO]).attempt
    } yield query
  }

  def storeDropboxAccessToken(
      userId: String,
      accessToken: Credential
  ): ConnectionIO[Int] = {
    sql"""UPDATE users
          SET dropbox_credential = ${accessToken}
          WHERE id = ${userId}
    """.update.run
  }

  def create(newUser: User.Create): ConnectionIO[User] = {
    val now = new Timestamp(new java.util.Date().getTime())

    (fr"INSERT INTO users (" ++ insertFieldsF ++ fr""")
       VALUES
          (${newUser.id}, ${UserRole
      .toString(newUser.role)}, ${now}, ${now}, '', '', false,
          ${newUser.email}, ${newUser.name}, ${newUser.profileImageUri}, false, true,
          ${UserVisibility.Private.toString}::user_visibility, DEFAULT, ${newUser.scope})
       """).update.withUniqueGeneratedKeys[User](
      fieldNames: _*
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
      searchParams: SearchQueryParameters
  ): ConnectionIO[List[User]] = {
    UserDao
      .viewFilter(user)
      .filter(searchParams)
      .list(0, 5, fr"order by name")
      .map(users => users map { sanitizeUser _ })
  }

  def updateOwnUser(user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val planetCredential = user.planetCredential.token.getOrElse("")
    for {
      query <- (
        sql"""
        UPDATE users
        SET
          modified_at = ${updateTime},
          planet_credential = ${planetCredential},
          email_notifications = ${user.emailNotifications},
          visibility = ${user.visibility},
          personal_info = ${user.personalInfo}
          """ ++
          Fragments.whereAndOpt(Some(fr"id = ${user.id}"))
      ).update.run
      _ <- remove(user.cacheKey)(userCache, async[ConnectionIO]).attempt
    } yield query
  }

  def findUsersByEmail(email: String): ConnectionIO[List[User]] =
    query
      .filter(fr"(email = $email OR personal_info ->> 'email' = $email)")
      .list

  def getThinUsersForIds(
      ids: NonEmptyList[String]
  ): ConnectionIO[List[UserThin]] =
    Nested(
      query
        .filter(Fragments.in(fr"id", ids))
        .list
    ).map(UserThin.fromUser(_)).value

  def createUserWithCampaign(
      userInfo: UserInfo,
      userBulkCreate: UserBulkCreate,
      parentUser: User
  ): ConnectionIO[UserWithCampaign] = {
    val userWithCampaignIO = for {
      user <- UserDao.create(
        User.Create(
          userInfo.id,
          email = userInfo.email,
          name = userInfo.name,
          scope = Scopes.GroundworkUser
        )
      )
      _ <- UserGroupRoleDao.createDefaultRoles(
        user,
        Some(userBulkCreate.platformId),
        Some(userBulkCreate.organizationId)
      )
      copiedCampaignO <- userBulkCreate.campaignId traverse { campaignId =>
        CampaignDao.copyCampaign(
          campaignId,
          user,
          None,
          userBulkCreate.copyResourceLink
        )
      }
    } yield UserWithCampaign(user, copiedCampaignO)

    userBulkCreate.grantAccessToParentCampaignOwner match {
      case false => userWithCampaignIO
      case true =>
        for {
          userAndCampaign <- userWithCampaignIO
          projectsO <- userAndCampaign.campaignO traverse { campaign =>
            AnnotationProjectDao.listByCampaign(campaign.id)
          }
          _ <- userAndCampaign.campaignO traverse { campaign =>
            CampaignDao.addPermission(
              campaign.id,
              ObjectAccessControlRule(
                SubjectType.User,
                Some(parentUser.id),
                ActionType.View
              )
            )
          }
          _ <- projectsO traverse { projects =>
            projects traverse { project =>
              AnnotationProjectDao.addPermissionsMany(
                project.id,
                List(
                  ObjectAccessControlRule(
                    SubjectType.User,
                    Some(parentUser.id),
                    ActionType.View
                  ),
                  ObjectAccessControlRule(
                    SubjectType.User,
                    Some(parentUser.id),
                    ActionType.Annotate
                  )
                )
              )
            }
          }
        } yield userAndCampaign
    }
  }

  def appendScope(userId: String, scope: Scope): ConnectionIO[Int] = {
    appendScope(userId, scope, true)
  }

  private[database] def appendScope(
      userId: String,
      scope: Scope,
      bust: Boolean
  ): ConnectionIO[Int] = {
    if (scope.actions.isEmpty) {
      0.pure[ConnectionIO]
    } else {
      fr"""update users set scopes = to_jsonb(trim(both '"' from cast(scopes :: jsonb as text)) || ${";" ++ scope.asJson.noSpaces
        .replace("\"", "")}) where id = $userId""".update.run <* (if (bust) {
                                                                    Cache
                                                                      .bust[
                                                                        ConnectionIO,
                                                                        User
                                                                      ](
                                                                        // I learned the cache key by telnet-ing to my local memcached and dumping all
                                                                        // the keys: https://stackoverflow.com/a/19562199
                                                                        // I'm assuming this isn't sensitive to the deployment environment, but it could
                                                                        // go wrong in staging if so (we'll learn really fast if it is).
                                                                        s"user:$userId"
                                                                      )
                                                                  } else {
                                                                    ().pure[
                                                                      ConnectionIO
                                                                    ]
                                                                  })
    }
  }

}
