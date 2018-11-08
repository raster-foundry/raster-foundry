package com.rasterfoundry.backsplash.auth

import com.rasterfoundry.authentication.Authentication
import com.rasterfoundry.backsplash.parameters.Parameters._
import com.rasterfoundry.database.{UserDao, MapTokenDao}
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel.{MapToken, User}

import cats.data._
import cats.effect.IO
import cats.implicits._
import doobie.ConnectionIO
import doobie.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server._
import org.http4s.util.CaseInsensitiveString

import java.util.UUID

object Authenticators extends Authentication {
  implicit val xa = RFTransactor.xa

  val tokensAuthenticator = Kleisli[OptionT[IO, ?], Request[IO], User](
    {
      case req @ _ -> "tools" /: UUIDWrapper(analysisId) /: _
            :? TokenQueryParamMatcher(tokenQP)
            :? MapTokenQueryParamMatcher(mapTokenQP) =>
        val authHeader: OptionT[IO, Header] =
          OptionT.fromOption(
            req.headers.get(CaseInsensitiveString("Authorization")))
        checkTokenAndHeader(tokenQP, authHeader) :+
          (
            OptionT.fromOption[IO](mapTokenQP) flatMap { (mapToken: UUID) =>
              userFromMapToken(MapTokenDao.checkAnalysis(analysisId), mapToken)
            }
          ) reduce { _ orElse _ }

      case req @ _ -> UUIDWrapper(projectId) /: _
            :? TokenQueryParamMatcher(tokenQP)
            :? MapTokenQueryParamMatcher(mapTokenQP) => {
        val authHeader: OptionT[IO, Header] =
          OptionT.fromOption(
            req.headers.get(CaseInsensitiveString("Authorization")))
        checkTokenAndHeader(tokenQP, authHeader) :+
          (
            OptionT.fromOption[IO](mapTokenQP) flatMap { (mapToken: UUID) =>
              userFromMapToken(MapTokenDao.checkProject(projectId), mapToken)
            }
          ) reduce { _ orElse _ }
      }

      case _ => OptionT.none[IO, User]
    }
  )

  private def checkTokenAndHeader(
      tokenQP: Option[String],
      authHeader: OptionT[IO, Header]): List[OptionT[IO, User]] = {
    List(
      authHeader flatMap { (header: Header) =>
        userFromToken(header.value.replace("Bearer ", ""))
      },
      OptionT.fromOption[IO](tokenQP) flatMap { (token: String) =>
        userFromToken(token)
      }
    )
  }

  private def userFromMapToken(func: UUID => ConnectionIO[Option[MapToken]],
                               mapTokenId: UUID): OptionT[IO, User] =
    OptionT(func(mapTokenId).transact(xa)) flatMap { mapToken =>
      OptionT(UserDao.getUserById(mapToken.owner).transact(xa))
    }

  private def userFromToken(token: String): OptionT[IO, User] =
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

  val tokensAuthMiddleware = AuthMiddleware(tokensAuthenticator)
}
