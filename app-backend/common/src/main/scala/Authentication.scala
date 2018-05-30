package com.azavea.rf.common

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
import com.azavea.rf.database._
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._


trait Authentication extends Directives {

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

  def getOrgCredentials(user: User): (Credential, Credential) = {
    val orgIO = for {
      ugrs <- UserGroupRoleDao.listByUserAndGroupType(user, GroupType.Organization)
      org <- OrganizationDao.getOrganizationById(ugrs.headOption match {
        case Some(ugr) => ugr.groupId
        case _ => throw new RuntimeException(
          s"No matching organization for user with id: ${user.id}")
      })
    } yield org
    orgIO.transact(xa).unsafeRunSync match {
      case Some(org) => (org.dropboxCredential, org.planetCredential)
      case _ => (Credential(Some("")), Credential(Some("")))
    }
  }

  def authenticateWithToken(tokenString: String): Directive1[User] = {
    val result = verifyJWT(tokenString)
    result match {
      case Left(e) => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
      case Right((_, jwtClaims)) => {
        val userId = jwtClaims.getStringClaim("sub")
        val email = jwtClaims.getStringClaim("email")
        val name = jwtClaims.getStringClaim("name")
        val picture = jwtClaims.getStringClaim("picture")
        onSuccess(UserDao.getUserById(userId).transact(xa).unsafeToFuture).flatMap {
          case Some(user) => {
            val (orgDropboxCredential, orgPlanetCredential) = getOrgCredentials(user)
            val updatedUser = (user.dropboxCredential, user.planetCredential) match {
              case (Credential(Some(d)), Credential(Some(p))) if d.length == 0 =>
                user.copy(email = email, name = name, profileImageUri = picture)
                  .copy(dropboxCredential = orgDropboxCredential)
              case (Credential(Some(d)), Credential(Some(p))) if p.length == 0 =>
                user.copy(email = email, name = name, profileImageUri = picture)
                  .copy(planetCredential = orgPlanetCredential)
              case _ => user.copy(email = email, name = name, profileImageUri = picture)
            }
            (updatedUser != user) match {
              case true =>
                onSuccess(
                  UserDao.updateUser(updatedUser, updatedUser.id)
                    .transact(xa)
                    .unsafeToFuture
                ).flatMap {
                  _ => provide(updatedUser)
                }
              case _ =>
                provide(user)
            }
          }
          case None => {
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

            val createUserQuery = for {
              platform <- PlatformDao.getPlatformById(platformId)
              systemUser <- UserDao.unsafeGetUserById(auth0SystemUser)
              newUser <- {
                val orgID = platform match {
                  case Some(p) => p.defaultOrganizationId.getOrElse(organizationId)
                  case _ => throw new RuntimeException(
                    s"Tried to create a user using a non-existent platformId: ${platformId}"
                  )
                }
                val jwtUser = User.JwtFields(
                  userId, email, name, picture,
                  platformId, orgID
                )
                UserDao.createUserWithJWT(systemUser, jwtUser)
              }
            } yield newUser

            onSuccess(
              createUserQuery.transact(xa).unsafeToFuture
            ).flatMap {
              user => provide(user)
            }
          }
        }
      }
    }
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
  def authenticateRootMember: Directive1[User] = {
    authenticate.flatMap { user =>
      if (user.isInRootOrganization) { provide(user) }
      else { reject(AuthorizationFailedRejection) }
    }
  }
}
