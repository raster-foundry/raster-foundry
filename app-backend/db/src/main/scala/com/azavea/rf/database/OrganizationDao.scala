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
      dropbox_credential, planet_credential, logo_uri
    FROM
  """ ++ tableF

  def create(
    org: Organization
  ): ConnectionIO[Organization] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
          (id, created_at, modified_at, name, platform_id, is_active)
        VALUES
          (${org.id}, ${org.createdAt}, ${org.modifiedAt}, ${org.name}, ${org.platformId}, true)
    """).update.withUniqueGeneratedKeys[Organization](
      "id", "created_at", "modified_at", "name", "platform_id", "is_active",
      "dropbox_credential", "planet_credential", "logo_uri"
    )
  }

  def getOrganizationById(id: UUID): ConnectionIO[Option[Organization]] =
    query.filter(id).selectOption

  def unsafeGetOrganizationById(id: UUID): ConnectionIO[Organization] =
    query.filter(id).select

  def createOrganization(newOrg: Organization.Create): ConnectionIO[Organization] = {
    create(newOrg.toOrganization)
  }

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

  def listMembers(organizationId: UUID, page: PageRequest, searchParams: SearchQueryParameters, actingUser: User): ConnectionIO[PaginatedResponse[User.WithGroupRole]] =
    UserGroupRoleDao.listUsersByGroup(GroupType.Organization, organizationId, page, searchParams, actingUser)


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

  def userIsAdmin(user: User, organizationId: UUID): ConnectionIO[Boolean] =
    userIsAdminF(user, organizationId).query[Boolean].option.map(_.getOrElse(false))

  def getOrgPlatformId(organizationId: UUID): ConnectionIO[UUID] =
    unsafeGetOrganizationById(organizationId) map { _.platformId }

  def addUserRole(actingUser: User, subjectId: String, organizationId: UUID, groupRole: GroupRole): ConnectionIO[UserGroupRole] = {
    val userGroupRoleCreate = UserGroupRole.Create(
      subjectId, GroupType.Organization, organizationId, groupRole
    )
    UserGroupRoleDao.create(userGroupRoleCreate.toUserGroupRole(actingUser))
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

  def setUserOrganization(actingUser: User, subjectId: String, organizationId: UUID, groupRole: GroupRole): ConnectionIO[List[UserGroupRole]] = {
    UserDao.getUserById(subjectId) flatMap {
      case Some(subjectUser) =>
        for {
          oldOrgRoles <- UserGroupRoleDao.listByUserAndGroupType(subjectUser, GroupType.Organization)
          // This is OK because we only expect there to be a single org role at a time
          deactivatedOrgRoles <- oldOrgRoles.traverse(
            (role) => {
              OrganizationDao.deactivateUserRoles(actingUser, subjectUser.id, role.groupId)
            })
          newOrgRoles <- OrganizationDao.setUserRole(actingUser, subjectUser.id, organizationId, groupRole)
        } yield (newOrgRoles ++ deactivatedOrgRoles.flatten)
      case None => throw new IllegalArgumentException(s"User not in database: ${subjectId}")
    }
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

    if (s3Client.listKeys(dataBucket, prefix).contains(s"${prefix}/${key}")) {
      s3Client.deleteObject(dataBucket, s"${prefix}/${key}")
    }

    s3Client.putObject(dataBucket, s"${prefix}/${key}", logoStream, md)
    s3.setObjectAcl(dataBucket, s"${prefix}/${key}", CannedAccessControlList.PublicRead)

    val uri = s"https://${dataBucket}.s3.amazonaws.com/${prefix}/${key}"
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    (fr"UPDATE" ++ tableF ++ fr"""SET
         modified_at = ${updateTime},
         logo_uri = ${uri}
       WHERE id = ${orgID}
     """).update.withUniqueGeneratedKeys[Organization](
       "id", "created_at", "modified_at", "name", "platform_id", "is_active",
       "dropbox_credential", "planet_credential", "logo_uri"
     )
  }
}
