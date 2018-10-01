package com.azavea.rf.backsplash.auth

import com.azavea.rf.authentication.Authentication
import com.azavea.rf.database.UserDao
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel.User

import cats.data._
import cats.effect.IO
import cats.implicits._
import doobie.implicits._
import org.http4s._
import org.http4s.server._
import org.http4s.dsl.io._

object TokenQueryParamMatcher
    extends QueryParamDecoderMatcher[String]("token")

object Authenticators extends Authentication {
  implicit val xa = RFTransactor.xa

  val queryParamAuthenticator = Kleisli[OptionT[IO, ?], Request[IO], User](
    {
      case req @ _ :? TokenQueryParamMatcher(token) => {
        verifyJWT(token) match {
          case Right((_, jwtClaims)) =>
            OptionT(
              UserDao
                .getUserById(jwtClaims.getStringClaim("sub"))
                .transact(xa)
            )
          case Left(e) =>
            OptionT(IO(None: Option[User]))
        }
      }
      case _ =>
        OptionT(IO(None: Option[User]))
    }
  )

  val queryParamAuthMiddleware = AuthMiddleware(queryParamAuthenticator)
}
