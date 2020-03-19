package com.rasterfoundry.akkautil

import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server._
import cats.data._
import cats.effect._
import cats.implicits._
import com.guizmaii.scalajwt.{ConfigurableJwtValidator, JwtToken}
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.Json

import scala.concurrent.Future
import scala.util.Try

import java.net.URL
import java.util.UUID

trait Authentication extends Directives with LazyLogging {

  implicit def xa: Transactor[IO]

  val configAuth = ConfigFactory.load()
  private val auth0Config = configAuth.getConfig("auth0")
  private val groundworkConfigAuth = configAuth.getConfig("groundwork")
  private val groundworkSampleProjectAuth =
    groundworkConfigAuth.getString("sampleProject")

  private val jwksURL = auth0Config.getString("jwksURL")
  private val jwkSet: JWKSource[SecurityContext] = new RemoteJWKSet(
    new URL(jwksURL)
  )

  // Default user returned when no credentials are provided
  lazy val anonymousUser: Future[Option[User]] =
    UserDao.getUserById("default").transact(xa).unsafeToFuture

  // HTTP Challenge to use for Authentication failures
  lazy val challenge = HttpChallenge("Bearer", "https://rasterfoundry.com")

  // Use on endpoints where you need to return an anonymous user for public endpoints
  def authenticateAllowAnonymous: Directive1[User] = {
    extractTokenHeader.flatMap {
      case Some(token) =>
        authenticateWithToken(token.split(" ").last)
      case None =>
        onSuccess(anonymousUser) flatMap {
          case Some(user) => provide(user)
          case _ =>
            reject(
              AuthenticationFailedRejection(CredentialsRejected, challenge)
            )
        }
    }
  }

  /**
    * Authenticates user based on bearer token (JWT)
    */
  def authenticate: Directive1[User] = {
    extractTokenHeader.flatMap {
      case Some(token) =>
        authenticateWithToken(token.split(" ").last)
      case None =>
        reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
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

  def verifyJWT(
      tokenString: String
  ): Either[BadJWTException, (JwtToken, JWTClaimsSet)] = {
    val token: JwtToken = JwtToken(content = tokenString)

    ConfigurableJwtValidator(jwkSet).validate(token)
  }

  def getStringClaimOrBlank(claims: JWTClaimsSet, key: String): String =
    Option(claims.getStringClaim(key)).getOrElse("")

  def getNameOrFallback(claims: JWTClaimsSet): String = {
    val delegatedProfile = Option(claims.getJSONObjectClaim("delegatedProfile"))
    val email = Option(claims.getStringClaim("email"))

    val compareToEmail = (field: String) =>
      (Option(claims.getStringClaim(field)), email) match {
        case (fld @ Some(f), Some(e)) if f != e => fld
        case (f, _)                             => f
    }

    val compareDelegatedToEmail = (field: String) =>
      (delegatedProfile.map(_.getAsString(field)), email) match {
        case (fld @ Some(f), Some(e)) if f != e => fld
        case (f, _)                             => f
    }

    compareToEmail("name")
      .orElse(compareToEmail("nickname"))
      .orElse(
        List(
          Option(claims.getStringClaim("given_name")),
          Option(claims.getStringClaim("family_name"))
        ).flatten.toNel.map(_.toList.mkString(" "))
      )
      .orElse(compareDelegatedToEmail("name"))
      .orElse(compareDelegatedToEmail("nickname"))
      .orElse(
        List(
          delegatedProfile.map(_.getAsString("given_name")),
          delegatedProfile.map(_.getAsString("family_name"))
        ).flatten.toNel.map(_.toList.mkString(" "))
      )
      .getOrElse(getStringClaimOrBlank(claims, "id"))
  }

  def defaultPersonalInfo(
      user: User,
      claims: JWTClaimsSet
  ): User.PersonalInfo = {
    val pi = user.personalInfo
    val delegatedProfile = Option(claims.getJSONObjectClaim("delegatedProfile"))

    val optionEmpty = (str: String) =>
      str match {
        case s if !s.trim.isEmpty => Some(s)
        case _                    => None
    }

    val defaultFromClaims = (field: String, str: String) =>
      optionEmpty(field)
        .orElse(Option(claims.getStringClaim(str)))
        .orElse(delegatedProfile.map(_.getAsString(str)))
        .getOrElse("")

    user.personalInfo.copy(
      firstName = defaultFromClaims(pi.firstName, "given_name"),
      lastName = defaultFromClaims(pi.lastName, "family_name"),
      email = defaultFromClaims(pi.email, "email")
    )
  }

  @SuppressWarnings(Array("TraversableHead", "PartialFunctionInsteadOfMatch"))
  def authenticateWithToken(tokenString: String): Directive1[User] = {
    val result = verifyJWT(tokenString)
    result match {
      case Left(_) =>
        reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
      case Right((_, jwtClaims)) => {
        val userId = jwtClaims.getStringClaim("sub")
        val email = getStringClaimOrBlank(jwtClaims, "email")
        val name = getNameOrFallback(jwtClaims)
        val picture = getStringClaimOrBlank(jwtClaims, "picture")
        final case class MembershipAndUser(
            platform: Option[Platform],
            user: User
        )
        // All users will have a platform role, either added by a migration or created with the user if they are new
        val query = for {
          (user, roles) <- OptionT[ConnectionIO, (User, List[UserGroupRole])] {
            UserDao.getUserAndActiveRolesById(userId).flatMap {
              case UserOptionAndRoles(Some(user), roles) =>
                Option((user, roles)).pure[ConnectionIO]
              case UserOptionAndRoles(None, _) => {
                createUserWithRoles(userId, email, name, picture, jwtClaims)
              }
            }
          }
          platformRole = roles.find(
            role => role.groupType == GroupType.Platform
          )
          plat <- OptionT {
            platformRole match {
              case Some(role) => PlatformDao.getPlatformById(role.groupId)
              case _ =>
                logger.error(
                  s"User without a platform tried to log in: ${userId}"
                )
                Option.empty[Platform].pure[ConnectionIO]
            }
          }
          personalInfo = defaultPersonalInfo(user, jwtClaims)
          updatedUser = (user.dropboxCredential, user.planetCredential) match {
            case (Credential(Some(d)), Credential(Some(_))) if d.length == 0 =>
              user.copy(
                email = email,
                name = name,
                profileImageUri = picture,
                personalInfo = personalInfo
              )
            case (Credential(Some(_)), Credential(Some(p))) if p.length == 0 =>
              user.copy(
                email = email,
                name = name,
                profileImageUri = picture,
                personalInfo = personalInfo
              )
            case _ =>
              user.copy(
                email = email,
                name = name,
                profileImageUri = picture,
                personalInfo = personalInfo
              )
          }
          userUpdate <- OptionT.liftF {
            (updatedUser != user) match {
              case true =>
                UserDao
                  .updateUser(updatedUser, updatedUser.id)
                  .map(_ => updatedUser)
              case _ => user.pure[ConnectionIO]
            }
          }
        } yield MembershipAndUser(Some(plat), userUpdate)
        onSuccess(query.value.transact(xa).unsafeToFuture).flatMap {
          case Some(MembershipAndUser(plat, userUpdate)) =>
            plat map { _.isActive } match {
              case Some(true) =>
                provide(userUpdate)
              case _ =>
                reject(
                  AuthenticationFailedRejection(CredentialsRejected, challenge)
                )
            }
          case _ =>
            reject(
              AuthenticationFailedRejection(CredentialsRejected, challenge)
            )
        }
      }
    }
  }

  def createUserWithRoles(
      userId: String,
      email: String,
      name: String,
      picture: String,
      jwtClaims: JWTClaimsSet
  ): ConnectionIO[Option[(User, List[UserGroupRole])]] = {
    // use default platform / org if fields are not filled
    val auth0DefaultPlatformId = auth0Config.getString("defaultPlatformId")
    val auth0DefaultOrganizationId =
      auth0Config.getString("defaultOrganizationId")
    val auth0SystemUser = auth0Config.getString("systemUser")

    val platformId = UUID.fromString(
      jwtClaims.getStringClaim(
        "https://app.rasterfoundry.com;platform"
      ) match {
        case platform: String => platform
        case _                => auth0DefaultPlatformId
      }
    )

    val organizationId = UUID.fromString(
      jwtClaims.getStringClaim(
        "https://app.rasterfoundry.com;organization"
      ) match {
        case organization: String => organization
        case _                    => auth0DefaultOrganizationId
      }
    )

    val userRole = getStringClaimOrBlank(
      jwtClaims,
      "https://app.rasterfoundry.com;platformRole"
    ).toUpperCase match {
      case "ADMIN" => GroupRole.Admin
      case _       => GroupRole.Member
    }

    val isGroundworkUser = jwtClaims
      .getBooleanClaim("https://app.rasterfoundry.com;annotateApp")
      .booleanValue()

    val userScope: Scope =
      Option(jwtClaims.getStringClaim("https://app.rasterfoundry.com;scopes")) match {
        case Some(scopeString) =>
          Json.fromString(scopeString).as[Scope] match {
            case Left(_)      => Scopes.NoAccess
            case Right(scope) => scope
          }
        case _ => Scopes.NoAccess
      }
    logger.debug(s"Setting user scopes: ${userScope}")

    for {
      platform <- PlatformDao.getPlatformById(platformId)
      systemUserO <- UserDao.getUserById(auth0SystemUser)
      systemUser = systemUserO match {
        case Some(su) => su
        case _ =>
          throw new RuntimeException(
            s"Tried to create a user using a non-existent system user: ${auth0SystemUser}"
          )
      }
      orgID = platform match {
        case Some(p) => p.defaultOrganizationId.getOrElse(organizationId)
        case _ =>
          throw new RuntimeException(
            s"Tried to create a user using a non-existent platformId: ${platformId}"
          )
      }
      jwtUser = User.JwtFields(
        userId,
        email,
        name,
        picture,
        platformId,
        orgID
      )
      newUserWithRoles <- {
        UserDao
          .createUserWithJWT(systemUser, jwtUser, userRole, userScope)
          .attempt
      }
      _ <- (newUserWithRoles, isGroundworkUser) match {
        case (Right(user), true) =>
          logger.info(s"sample project id = ${groundworkSampleProjectAuth}")
          AnnotationProjectDao.copyProject(
            UUID.fromString(groundworkSampleProjectAuth),
            user._1
          )
        case _ => Unit.pure[ConnectionIO]
      }
      createdUserWithRoles <- UserDao.getUserAndActiveRolesById(userId)
    } yield createdUserWithRoles.user.map((_, createdUserWithRoles.roles))
  }

  /**
    * Helper directive to extract token header
    */
  def extractTokenHeader: Directive1[Option[String]] = {
    optionalHeaderValueByName("Authorization")
  }

  /**
    * Helper directive to extract maptoken param
    */
  def extractMapTokenParam: Directive1[Option[UUID]] = {
    parameter('mapToken.?) flatMap { mapTokenO =>
      provide(mapTokenO flatMap { tokenString =>
        Try { UUID.fromString(tokenString) } toOption
      })
    }
  }

  /**
    * Directive that only allows members of root organization
    */
  def authenticateSuperUser: Directive1[User] = {
    authenticate.flatMap { user =>
      if (user.isSuperuser) {
        provide(user)
      } else {
        reject(AuthorizationFailedRejection)
      }
    }
  }
}
