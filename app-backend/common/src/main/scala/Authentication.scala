package com.azavea.rf.common

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables._
import com.azavea.rf.datamodel._
import com.typesafe.config.ConfigFactory
import org.json4s._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtJson4s}

import scala.concurrent.Future
import scala.util.{Failure, Success}

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
    validateTokenHeader.flatMap { validToken =>
      JwtJson4s.decodeJson(validToken, auth0Secret, Seq(JwtAlgorithm.HS256)) match {
        case Success(parts) =>
          val sub = (parts \ "sub").extract[String]

          onSuccess(Users.getUserById(sub)).flatMap {
            case Some(user) => provide(user)
            case None => onSuccess(Users.createUserWithAuthId(sub)).flatMap {
              user => provide(user)
            }
          }
        case Failure(_) => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
      }
    }
  }

  /**
    * Authenticates user based on bearer token (JWT)
    */
  def authenticateWithParameter: Directive1[User] = {
    validateTokenParameter.flatMap { validToken =>
      getUserWithJWT(validToken)
    }
  }

  def authenticateWithToken(token: String): Directive1[User] = {
    if (Jwt.isValid(token, auth0Secret, Seq(JwtAlgorithm.HS256))) {
      getUserWithJWT(token)
    } else {
      reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
    }
  }

  def getUserWithJWT(token: String): Directive1[User] = {
    JwtJson4s.decodeJson(token, auth0Secret, Seq(JwtAlgorithm.HS256)) match {
      case Success(parts) =>
        val sub = (parts \ "sub").extract[String]

        val userFromId = Users.getUserById(sub)

        onSuccess(userFromId).flatMap {
          case Some(user) => provide(user)
          case None => onSuccess(Users.createUserWithAuthId(sub)).flatMap {
            user => provide(user)
          }
        }
      case Failure(_) => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
    }
  }

  /**
    * Validates a token parameter and optionally returns it if valid, else rejects request
    */
  def validateTokenParameter: Directive1[String] = {
    parameter('token).flatMap { token =>
      if(Jwt.isValid(token, auth0Secret, Seq(JwtAlgorithm.HS256))) { provide(token) }
      else { reject(AuthenticationFailedRejection(CredentialsRejected, challenge)) }
    }
  }

  /**
    * Validates a token parameter and returns true if valid, false otherwise
    */
  def isTokenParameterValid: Directive1[Boolean] = {
    parameter('token).flatMap { token =>
      provide(Jwt.isValid(token, auth0Secret, Seq(JwtAlgorithm.HS256)))
    }
  }

  /**
    * Validates token header, if valid returns token else rejects request
    */
  def validateTokenHeader: Directive1[String] = {
    extractTokenHeader.flatMap { token =>
      if(Jwt.isValid(token, auth0Secret, Seq(JwtAlgorithm.HS256))) { provide(token) }
      else { reject(AuthenticationFailedRejection(CredentialsRejected, challenge)) }
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
