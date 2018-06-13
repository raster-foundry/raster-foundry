package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import geotrellis.spark.io.s3.AmazonS3Client
import com.amazonaws.services.s3.{AmazonS3Client => AWSAmazonS3Client}
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.s3.model.{ObjectMetadata, CannedAccessControlList}

import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest

import java.util.UUID
import java.sql.Timestamp
import org.apache.commons.codec.binary.{Base64 => ApacheBase64}
import java.io.ByteArrayInputStream

import scala.concurrent.Future
import com.typesafe.scalalogging.LazyLogging
import scala.util.Properties


object OrganizationDao extends Dao[Organization] with LazyLogging {

  val tableName = "organizations"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, name, platform_id, is_active,
      dropbox_credential, planet_credential, logo_uri, visibility
    FROM
  """ ++ tableF

  def createUserGroupRole = UserGroupRoleDao.createWithGuard(userIsAdmin, GroupType.Organization) _

  def create(
    org: Organization
  ): ConnectionIO[Organization] =
    (fr"INSERT INTO" ++ tableF ++ fr"""
          (id, created_at, modified_at, name, platform_id, is_active, visibility)
        VALUES
          (${org.id}, ${org.createdAt}, ${org.modifiedAt}, ${org.name}, ${org.platformId}, true, ${org.visibility})
    """).update.withUniqueGeneratedKeys[Organization](
      "id", "created_at", "modified_at", "name", "platform_id", "is_active",
      "dropbox_credential", "planet_credential", "logo_uri", "visibility"
    )

  def getOrganizationById(id: UUID): ConnectionIO[Option[Organization]] =
    query.filter(id).selectOption

  def unsafeGetOrganizationById(id: UUID): ConnectionIO[Organization] =
    query.filter(id).select

  def createOrganization(newOrg: Organization.Create): ConnectionIO[Organization] =
    create(newOrg.toOrganization)

  def update(org: Organization, id: UUID): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)

    (fr"UPDATE" ++ tableF ++ fr"""SET
         modified_at = ${updateTime},
         name = ${org.name},
         dropbox_credential = ${org.dropboxCredential.token.getOrElse("")},
         planet_credential = ${org.planetCredential.token.getOrElse("")}
       WHERE id = ${id}
     """).update.run
  }

  def listMembers(organizationId: UUID, page: PageRequest, searchParams: SearchQueryParameters, actingUser: User): ConnectionIO[PaginatedResponse[User.WithGroupRole]] = for {
    organizationO <- OrganizationDao.getOrganizationById(organizationId)
    isDefaultOrg <- organizationO match {
      case Some(org) => PlatformDao.organizationIsPublicOrg(organizationId, org.platformId)
      case None => false.pure[ConnectionIO]
    }
    usersPage <- UserGroupRoleDao.listUsersByGroup(GroupType.Organization, organizationId, page, searchParams, actingUser)
    maybeSanitized = isDefaultOrg match {
      case true => usersPage.copy(
        results = usersPage.results map { _.copy(email = "") }
      )
      case false => usersPage
    }
  } yield { maybeSanitized }

  def validatePath(platformId: UUID,
                   organizationId: UUID): ConnectionIO[Boolean] =
    (fr"""
      SELECT count(o.id) > 0
      FROM """ ++ tableF ++ fr""" o
      JOIN """ ++ PlatformDao.tableF ++ fr""" p
        ON p.id = o.platform_id
      WHERE
        p.id = ${platformId} AND
        o.id = ${organizationId}
    """).query[Boolean].option.map(_.getOrElse(false))


  def userIsMemberF(user: User, organizationId: UUID ) = fr"""
    SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
        SELECT count(id) > 0
        FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
        WHERE
          user_id = ${user.id} AND
          group_type = ${GroupType.Organization.toString}::group_type AND
          group_id = ${organizationId} AND
          is_active = true
      ) OR (
      SELECT count(ugr.id) > 0
      FROM""" ++ PlatformDao.tableF ++ fr"""AS p
      JOIN""" ++ tableF ++ fr"""o
        ON o.platform_id = p.id
      JOIN""" ++ UserGroupRoleDao.tableF ++ fr"""ugr
        ON ugr.group_id = p.id
      WHERE
        o.id = ${organizationId} AND
        ugr.user_id = ${user.id} AND
        ugr.group_role = ${GroupRole.Admin.toString}::group_role AND
        ugr.group_type = ${GroupType.Platform.toString}::group_type AND
        ugr.is_active = true
    )
  """

  def userIsMember(user: User, organizationId: UUID): ConnectionIO[Boolean] =
    userIsMemberF(user, organizationId).query[Boolean].option.map(_.getOrElse(false))

  def userIsAdminF(user: User, organizationId: UUID) = fr"""
    SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
      SELECT count(id) > 0
      FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
      WHERE
        user_id = ${user.id} AND
        group_type = ${GroupType.Organization.toString}::group_type AND
        group_role = ${GroupRole.Admin.toString}::group_role AND
        group_id = ${organizationId} AND
        is_active = true AND
        membership_status = 'APPROVED'
    ) OR (
      SELECT count(ugr.id) > 0
      FROM""" ++ PlatformDao.tableF ++ fr"""AS p
      JOIN""" ++ tableF ++ fr"""o
        ON o.platform_id = p.id
      JOIN""" ++ UserGroupRoleDao.tableF ++ fr"""ugr
        ON ugr.group_id = p.id
      WHERE
        o.id = ${organizationId} AND
        ugr.user_id = ${user.id} AND
        ugr.group_role = ${GroupRole.Admin.toString}::group_role AND
        ugr.group_type = ${GroupType.Platform.toString}::group_type AND
        ugr.is_active = true AND
        membership_status = 'APPROVED'
    )
  """

  def userIsAdmin(user: User, organizationId: UUID): ConnectionIO[Boolean] =
    userIsAdminF(user, organizationId).query[Boolean].option.map(_.getOrElse(false))

  def getOrgPlatformId(organizationId: UUID): ConnectionIO[UUID] =
    unsafeGetOrganizationById(organizationId) map { _.platformId }

  def addUserRole(actingUser: User, subjectId: String, organizationId: UUID, groupRole: GroupRole): ConnectionIO[UserGroupRole] = {
    val userGroupRoleCreate = UserGroupRole.Create(
      subjectId, GroupType.Organization, organizationId, groupRole
    )

    createUserGroupRole(organizationId, actingUser, subjectId, userGroupRoleCreate)
  }

  def setUserRole(actingUser: User, subjectId: String, organizationId: UUID, groupRole: GroupRole):
      ConnectionIO[List[UserGroupRole]] = {
    for {
      orgRoles <-
        OrganizationDao
          .deactivateUserRoles(
            actingUser, subjectId, organizationId
          ).flatMap(
            (deactivatedRoles) => {
              OrganizationDao.addUserRole(actingUser, subjectId, organizationId, groupRole)
                .map((role) => deactivatedRoles ++ List(role))
            }
          )
      platformId <- getOrgPlatformId(organizationId)
      platformRoles <-
        UserGroupRoleDao
          .listUserGroupRoles(GroupType.Platform, platformId, subjectId)
          .flatMap(
            (roles: List[UserGroupRole]) => roles match {
              case Nil =>
                PlatformDao.setUserRole(actingUser, subjectId, platformId, GroupRole.Member)
              case roles =>
                List.empty[UserGroupRole].pure[ConnectionIO]
            }
          )
    } yield (platformRoles ++ orgRoles)
  }

  def deactivateUserRoles(actingUser: User, subjectId: String, organizationId: UUID): ConnectionIO[List[UserGroupRole]] = {
    val userGroup = UserGroupRole.UserGroup(
      subjectId, GroupType.Organization, organizationId
    )
    UserGroupRoleDao.deactivateUserGroupRoles(userGroup, actingUser)
  }

  def addLogo(logoBase64: String, orgID: UUID, dataBucket: String): ConnectionIO[Organization] = {
    val prefix = "org-logos"
    val key = s"${orgID.toString()}.png"
    val logoByte = ApacheBase64.decodeBase64(logoBase64)
    val logoStream = new ByteArrayInputStream(logoByte)
    val md = new ObjectMetadata()
    val s3 = new AWSAmazonS3Client(new DefaultAWSCredentialsProviderChain)
    val s3Client = new AmazonS3Client(s3)

    md.setContentType("image/png")
    md.setContentLength(logoByte.length)

    s3Client.putObject(dataBucket, s"${prefix}/${key}", logoStream, md)
    s3.setObjectAcl(dataBucket, s"${prefix}/${key}", CannedAccessControlList.PublicRead)

    val uri = s"https://s3.amazonaws.com/${dataBucket}/${prefix}/${key}"
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    (fr"UPDATE" ++ tableF ++ fr"""SET
         modified_at = ${updateTime},
         logo_uri = ${uri}
       WHERE id = ${orgID}
     """).update.withUniqueGeneratedKeys[Organization](
       "id", "created_at", "modified_at", "name", "platform_id", "is_active",
       "dropbox_credential", "planet_credential", "logo_uri", "visibility"
     )
  }

  def setVisibility(visibility: Visibility, organizationId: UUID): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    (fr"UPDATE" ++ tableF ++ fr"""
        modified_at = ${updateTime},
        visibility = ${visibility}
        """).update.run
  }

  def activateOrganization(actingUser: User, organizationId: UUID) = {
    (fr"UPDATE" ++ tableF ++ fr"""SET
       is_active = true,
       modified_at = now()
       where id = ${organizationId}
      """).update.run
  }

  def deactivateOrganization(actingUser: User, organizationId: UUID) = {
    (fr"UPDATE" ++ tableF ++ fr"""SET
       is_active = false,
       modified_at = now()
       where id = ${organizationId}
      """).update.run
  }

  /*
   Filter to platforms that:
   - user is a member of
   - are public
   - user is admin of the platform
   */
  def viewFilter(user: User): Dao.QueryBuilder[Organization] =
    Dao.QueryBuilder[Organization](
      user.isSuperuser match {
        case true =>
          selectF
        case _ =>
          (selectF ++ fr"""
        JOIN (
          -- user is member or admin of org
          SELECT ugr1.group_id as org_id
          FROM user_group_roles ugr1
          WHERE ugr1.group_type = 'ORGANIZATION' AND ugr1.user_id = ${user.id}

          UNION
          -- if user is admin of platform
          SELECT org.id AS org_id FROM user_group_roles ugr2
          JOIN platforms ON ugr2.group_id = platforms.id
          JOIN organizations org ON org.platform_id = platforms.id
          WHERE ugr2.group_type = 'PLATFORM' AND ugr2.group_role = 'ADMIN' AND ugr2.user_id = ${user.id}

          UNION

          -- org is public and user is in same platform
          SELECT org2.id AS org_id FROM""" ++ tableF ++ fr"""org2
          JOIN (
            SELECT p.id AS pid
            FROM platforms p JOIN user_group_roles ugr3
            ON ugr3.group_id = p.id
            WHERE ugr3.user_id = ${user.id}
          ) AS platformids ON org2.platform_id = platformids.pid
          WHERE org2.visibility = 'PUBLIC'
        ) AS org_ids ON """ ++ Fragment.const(s"${tableName}.id") ++ fr"= org_ids.org_id")
      }, tableF, List.empty
    )

  def listAuthorizedOrganizations(pageRequest: PageRequest, searchParams: SearchQueryParameters, platformId: UUID, user: User): ConnectionIO[PaginatedResponse[Organization]] =  {
    val organizationSearchBuilder = {
      OrganizationDao.viewFilter(user)
        .filter(fr"platform_id=${platformId}")
        .filter(searchParams)
    }
    for {
      organizations <- organizationSearchBuilder.list((pageRequest.offset * pageRequest.limit), pageRequest.limit)
      count <- organizationSearchBuilder.countIO
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = ((pageRequest.offset + 1) * pageRequest.limit) < count
      PaginatedResponse[Organization](
        count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, organizations
      )
    }
  }
}
