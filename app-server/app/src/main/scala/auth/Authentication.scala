package com.azavea.rf.auth

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected

import de.choffmeister.auth.common.JsonWebToken

import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.{Config, Database}
import com.azavea.rf.user.UserService


trait Authentication extends Directives with Config {
  implicit def database: Database
  implicit val ec: ExecutionContext

  // Default user returned when no credentials are provided
  lazy val anonymousUser:Future[Option[UsersRow]] = UserService.getUserById("default")

  // HTTP Challenge to use for Authentication failures
  lazy val challenge = HttpChallenge("Bearer", "https://rasterfoundry.com")

  /**
    * Handle validating Json Web Token - optionally returns token
    *
    * @param bearerToken bearer token of the form Bearer <token>
    */
  def validateJWT(bearerToken: String): Directive1[UsersRow] = {
    val token = bearerToken.split(" ").last
    val jwt = JsonWebToken.read(token, auth0Secret) match {
      case Right(token) => Some(token)
      case _ => None
    }
    jwt match {
      case Some(validToken) => {
        validToken.claimAsString("sub") match {
          case Right(sub) => {
            onSuccess(UserService.getUserById(sub)).flatMap {
              case Some(user) => provide(user)
              case None => onSuccess(UserService.createUserWithAuthId(sub)).flatMap {
                case Success(user) => provide(user)
                case Failure(_) => complete(StatusCodes.InternalServerError)
              }
            }
          }
          case Left(_) => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
        }
      }
      case _ => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
    }
  }

  /**
    * Authenticates requests and provides a User to requests
    *
    * - Rejects invalid credentials
    * - Allows anonymous users if credentials are provided
    * - If credentials are valid, but user does not exist, rejects
    */
  def authenticateAndAllowAnonymous: Directive1[UsersRow] = {
    optionalHeaderValueByName("Authorization").flatMap {
      case Some(token) => validateJWT(token)
      // No credentials = anonymous user, if can't get that then an error occurred
      case _ => onSuccess(anonymousUser).flatMap {
        case Some(user) => provide(user)
        case None => complete(StatusCodes.InternalServerError)
      }
    }
  }

  /**
    * Authenticates requests and requires a valid user
    *
    */
  def authenticate: Directive1[UsersRow] = {
    optionalHeaderValueByName("Authorization").flatMap {
      case Some(token) => validateJWT(token)
      case _ => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
    }
  }
}
