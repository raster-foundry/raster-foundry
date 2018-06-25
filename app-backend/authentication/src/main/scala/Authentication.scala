package com.azavea.rf.authentication

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server._
import com.azavea.rf.datamodel._
import com.guizmaii.scalajwt.JwtToken
import com.guizmaii.scalajwt.ConfigurableJwtValidator
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import java.net.URL
import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.azavea.rf.database._
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

import com.typesafe.scalalogging.LazyLogging

trait Authentication extends Directives with LazyLogging {

  implicit def xa: Transactor[IO]

  val configAuth = ConfigFactory.load()
  private val auth0Config = configAuth.getConfig("auth0")

  private val jwksURL = auth0Config.getString("jwksURL")
  private val jwkSet: JWKSource[SecurityContext] = new RemoteJWKSet(new URL(jwksURL))

  // Default user returned when no credentials are provided
  lazy val anonymousUser:Future[Option[User]] = UserDao.getUserById("default").transact(xa).unsafeToFuture

  // HTTP Challenge to use for Authentication failures
  lazy val challenge = HttpChallenge("Bearer", "https://rasterfoundry.com")

  /**
    * Authenticates user based on bearer token (JWT)
    */
  def authenticate: Directive1[User] = {
    extractTokenHeader.flatMap { token =>
      authenticateWithToken(token)
    }
  }

  /**
    * Authenticates user based on bearer token (JWT)
    */
  def authenticateWithParameter: Directive1[User] = {
    parameter('token).flatMap { token =>
      authenticateWithToken(token)
    }
  }

  def verifyJWT(tokenString: String): Either[BadJWTException, (JwtToken, JWTClaimsSet)] = {
    val token: JwtToken = JwtToken(content = tokenString)

    ConfigurableJwtValidator(jwkSet).validate(token)
  }

  def getOrgCredentials(user: User): ConnectionIO[(Credential, Credential)] = {
    val orgIO = for {
      ugrs <- UserGroupRoleDao.listByUserAndGroupType(user, GroupType.Organization)
      org <- OrganizationDao.getOrganizationById(ugrs.headOption match {
        case Some(ugr) => ugr.groupId
        case _ => throw new RuntimeException(
          s"No matching organization for user with id: ${user.id}")
      })
    } yield org
    orgIO.map {
      case Some(org) => (org.dropboxCredential, org.planetCredential)
      case _ => (Credential(Some("")), Credential(Some("")))
    }
  }

  def getStringClaimOrBlank(claims: JWTClaimsSet, key: String): String =
    Option(claims.getStringClaim(key)).getOrElse("")

  @SuppressWarnings(Array("TraversableHead"))
  def authenticateWithToken(tokenString: String): Directive1[User] = {
    val result = verifyJWT(tokenString)
    result match {
      case Left(e) => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
      case Right((_, jwtClaims)) => {
        val userId = jwtClaims.getStringClaim("sub")
        val email = getStringClaimOrBlank(jwtClaims, "email")
        val name = getStringClaimOrBlank(jwtClaims, "name")
        val picture = getStringClaimOrBlank(jwtClaims, "picture")
        case class MembershipAndUser(platform: Option[Platform], organization: Option[Organization], user: User)
        // All users will have an org role, either added by a migration or created with the user if they are new
        // All users will have a platform role, either added by a migration or created with the user if they are new
        val query = for {
          userAndRoles <- UserDao.getUserAndActiveRolesById(userId).flatMap {
            case UserOptionAndRoles(Some(user), roles) => (user, roles).pure[ConnectionIO]
            case UserOptionAndRoles(None, _) => createUserWithRoles(userId, email, name, picture, jwtClaims)
          }
          (user, roles) = userAndRoles
          orgRole = roles.filter(role => role.groupType == GroupType.Organization).headOption
          org <- orgRole match {
            case Some(role ) => OrganizationDao.getOrganizationById(role.groupId)
            case _ =>
              logger.error(s"User without an organization tried to log in: ${userId}")
              None.pure[ConnectionIO]
          }
          platformRole = roles.filter(role => role.groupType == GroupType.Platform).headOption
          plat <- platformRole match {
            case Some(role) => PlatformDao.getPlatformById(role.groupId)
            case _ =>
              logger.error(s"User without a platform tried to log in: ${userId}")
              None.pure[ConnectionIO]
          }
          orgCredentials <- (plat, org) match {
            case (Some(_), Some(_)) => getOrgCredentials(user)
            case _ => (Credential(None), Credential(None)).pure[ConnectionIO]
          }
          (orgDropboxCredential, orgPlanetCredential) = orgCredentials
          updatedUser = (user.dropboxCredential, user.planetCredential) match {
            case (Credential(Some(d)), Credential(Some(p))) if d.length == 0 =>
              user.copy(email = email, name = name, profileImageUri = picture)
                .copy(dropboxCredential = orgDropboxCredential)
            case (Credential(Some(d)), Credential(Some(p))) if p.length == 0 =>
              user.copy(email = email, name = name, profileImageUri = picture)
                .copy(planetCredential = orgPlanetCredential)
            case _ => user.copy(email = email, name = name, profileImageUri = picture)
          }
          userUpdate <- {
            (updatedUser != user) match {
              case true => UserDao.updateUser(updatedUser, updatedUser.id).map(_ => updatedUser)
              case _ => user.pure[ConnectionIO]
            }
          }
        } yield MembershipAndUser(plat, org, userUpdate)
        onSuccess(query.transact(xa).unsafeToFuture).flatMap {
          case MembershipAndUser(plat, org, userUpdate) =>
            (plat map { _.isActive }, org map { _.status }) match {
              case (Some(true), Some(OrgStatus.Active)) =>
                provide(userUpdate)
              case _ =>
                reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
            }
          case _ =>
            reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
        }
      }
    }
  }

  def createUserWithRoles(userId: String, email: String, name: String, picture: String, jwtClaims: JWTClaimsSet): ConnectionIO[(User, List[UserGroupRole])] = {
    // use default platform / org if fields are not filled
    val auth0DefaultPlatformId = auth0Config.getString("defaultPlatformId")
    val auth0DefaultOrganizationId = auth0Config.getString("defaultOrganizationId")
    val auth0SystemUser = auth0Config.getString("systemUser")

    val platformId = UUID.fromString(
      jwtClaims.getStringClaim(
        "https://app.rasterfoundry.com;platform"
      ) match {
        case platform: String => platform
        case _ => auth0DefaultPlatformId
      }
    )

    val organizationId = UUID.fromString(
      jwtClaims.getStringClaim(
        "https://app.rasterfoundry.com;organization"
      ) match {
        case organization: String => organization
        case _ => auth0DefaultOrganizationId
      }
    )

    for {
      platform <- PlatformDao.getPlatformById(platformId)
      systemUserO <- UserDao.getUserById(auth0SystemUser)
      systemUser = systemUserO match {
        case Some(su) => su
        case _ => throw new RuntimeException(
          s"Tried to create a user using a non-existent system user: ${auth0SystemUser}"
        )
      }
      orgID = platform match {
        case Some(p) => p.defaultOrganizationId.getOrElse(organizationId)
        case _ => throw new RuntimeException(
          s"Tried to create a user using a non-existent platformId: ${platformId}"
        )
      }
      jwtUser = User.JwtFields(
        userId, email, name, picture,
        platformId, orgID
      )
      newUserWithRoles <- {
        UserDao.createUserWithJWT(systemUser, jwtUser)
      }
      (newUser, roles) = newUserWithRoles
    } yield (newUser, roles)
  }

  /**
    * Helper directive to extract token header
    */
  def extractTokenHeader: Directive1[String] = {
    optionalHeaderValueByName("Authorization").flatMap {
      case Some(tokenString) => provide(tokenString.split(" ").last)
      case _ => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
    }
  }

  /**
    * Directive that only allows members of root organization
    */
  def authenticateSuperUser: Directive1[User] = {
    authenticate.flatMap { user =>
      if (user.isSuperuser) { provide(user) }
      else { reject(AuthorizationFailedRejection) }
    }
  }
}
