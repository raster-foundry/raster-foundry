package com.azavea.rf.backsplash

import com.azavea.rf.authentication
import com.azavea.rf.database._
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel._

import cats.data._
import cats.effect.IO
import cats.implicits._
import doobie.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server._

object TileAuthentication extends authentication.Authentication {

  implicit val xa = RFTransactor.xa

  val authUser: Kleisli[IO, Request[IO], Either[String, IO[User]]] =
    Kleisli(
      req =>
        IO(
          for {
            message <- req.headers
              .get(Authorization)
              .toRight("Couldn't find an auth header")
            jwtResult <- {
              verifyJWT(message.toString.replace("Authorization: Bearer ", "")).left map {
                _ =>
                  "Failed to verify jwt"
              }
            }
          } yield {
            val (_, jwtClaims) = jwtResult
            val userId = jwtClaims.getStringClaim("sub")
            println(s"User id: $userId")
            UserDao.unsafeGetUserById(userId).transact(xa)
          }
      )
    )

  val onFailure: AuthedService[String, IO] =
    Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))

  val authMiddleware = AuthMiddleware(authUser, onFailure)
}
