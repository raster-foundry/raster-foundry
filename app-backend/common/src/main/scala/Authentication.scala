package com.azavea.rf.common

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables._
import com.azavea.rf.datamodel._
import com.guizmaii.scalajwt.JwtToken
import com.guizmaii.scalajwt.ConfigurableJwtValidator
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException
import com.typesafe.config.ConfigFactory
import org.json4s._

import scala.concurrent.Future
import java.net.URL

trait Authentication extends Directives {

  implicit def database: Database
  implicit val formats = DefaultFormats

  val configAuth = ConfigFactory.load()
  private val auth0Config = configAuth.getConfig("auth0")
  private val auth0Secret = auth0Config.getString("secret")

  // Default user returned when no credentials are provided
  lazy val anonymousUser:Future[Option[User]] = Users.getUserById("default")

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
    val url = "https://raster-foundry-dev.auth0.com/.well-known/jwks.json"
    val jwkSet: JWKSource[SecurityContext] = new RemoteJWKSet(new URL(url))
    ConfigurableJwtValidator(jwkSet).validate(token)
  }

  def authenticateWithToken(tokenString: String): Directive1[User] = {
    val result = verifyJWT(tokenString)
    result match {
      case Left(e) => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
      case Right((_, jwtClaims)) => {
        val userId = jwtClaims.getStringClaim("sub")
        onSuccess(Users.getUserById(userId)).flatMap {
          case Some(user) => provide(user)
          case None => onSuccess(Users.createUserWithAuthId(userId)).flatMap {
            user => provide(user)
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
