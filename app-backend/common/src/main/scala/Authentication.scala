package com.azavea.rf.common

import scala.concurrent.Future
import akka.http.scaladsl.model.headers.{HttpChallenge, _}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.directives.ParameterDirectives.ParamMagnet
import akka.http.scaladsl.server.Directives._
import de.choffmeister.auth.common.JsonWebToken
import com.azavea.rf.database.tables._
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.typesafe.config.ConfigFactory

trait Authentication extends Directives {

  implicit def database: Database

  val configAuth = ConfigFactory.load()
  private val auth0Config = configAuth.getConfig("auth0")
  private val auth0Secret = java.util.Base64.getUrlDecoder.decode(auth0Config.getString("secret"))

  // Default user returned when no credentials are provided
  lazy val anonymousUser:Future[Option[User]] = Users.getUserById("default")

  // HTTP Challenge to use for Authentication failures
  lazy val challenge = HttpChallenge("Bearer", "https://rasterfoundry.com")

  /**
    * Authenticates user based on bearer token (JWT)
    */
  def authenticate: Directive1[User] = {
    validateTokenHeader.flatMap { validToken =>
      validToken.claimAsString("sub") match {
        case Right(sub) =>
          onSuccess(Users.getUserById(sub)).flatMap {
            case Some(user) => provide(user)
            case None => onSuccess(Users.createUserWithAuthId(sub)).flatMap {
              user => provide(user)
            }
          }
        case Left(_) => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
      }
    }
  }

  /**
    * Validates a token parameter and optionally returns it if validd, else rejects request
    */
  def validateTokenParameter: Directive1[JsonWebToken] = {
    parameter('token).flatMap { token =>

      JsonWebToken.read(token, auth0Secret) match {
        case Right(token) => {
          provide(token)
        }
        case _ => {
          reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
        }
      }
    }
  }

  /**
    * Validates token header, if valid returns token else rejects request
    */
  def validateTokenHeader: Directive1[JsonWebToken] = {
    extractTokenHeader.flatMap { token =>
      JsonWebToken.read(token, auth0Secret) match {
        case Right(token) => provide(token)
        case _ => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
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
}
