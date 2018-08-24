package com.azavea.rf.authentication

import java.net.URL
import java.util.UUID

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server._
import cats.effect.IO
import cats.implicits._
import com.azavea.rf.database._
import com.azavea.rf.datamodel._
import com.guizmaii.scalajwt.{ConfigurableJwtValidator, JwtToken}
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor

import scala.concurrent.Future

trait Authentication extends Directives with LazyLogging {

  implicit def xa: Transactor[IO]

  val configAuth = ConfigFactory.load()
  private val auth0Config = configAuth.getConfig("auth0")

  private val jwksURL = auth0Config.getString("jwksURL")
  private val jwkSet: JWKSource[SecurityContext] = new RemoteJWKSet(new URL(jwksURL))

  // Default user returned when no credentials are provided
  lazy val anonymousUser: Future[Option[User]] = UserDao.getUserById("default").transact(xa).unsafeToFuture

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
        final case class MembershipAndUser(platform: Option[Platform], user: User)
        // All users will have a platform role, either added by a migration or created with the user if they are new
        val query = for {
          userAndRoles <- UserDao.getUserAndActiveRolesById(userId).flatMap {
            case UserOptionAndRoles(Some(user), roles) => (user, roles).pure[ConnectionIO]
            case UserOptionAndRoles(None, _) => createUserWithRoles(userId, email, name, picture, jwtClaims)
          }
          (user, roles) = userAndRoles
          platformRole = roles.find(role => role.groupType == GroupType.Platform)
          plat <- platformRole match {
            case Some(role) => PlatformDao.getPlatformById(role.groupId)
            case _ =>
              logger.error(s"User without a platform tried to log in: ${userId}")
              None.pure[ConnectionIO]
          }
          updatedUser = (user.dropboxCredential, user.planetCredential) match {
            case (Credential(Some(d)), Credential(Some(p))) if d.length == 0 =>
              user.copy(email = email, name = name, profileImageUri = picture)
            case (Credential(Some(d)), Credential(Some(p))) if p.length == 0 =>
              user.copy(email = email, name = name, profileImageUri = picture)
            case _ => user.copy(email = email, name = name, profileImageUri = picture)
          }
          userUpdate <- {
            (updatedUser != user) match {
              case true => UserDao.updateUser(updatedUser, updatedUser.id).map(_ => updatedUser)
              case _ => user.pure[ConnectionIO]
            }
          }
        } yield MembershipAndUser(plat, userUpdate)
        onSuccess(query.transact(xa).unsafeToFuture).flatMap {
          case MembershipAndUser(plat, userUpdate) =>
            plat map { _.isActive } match {
              case Some(true) =>
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
