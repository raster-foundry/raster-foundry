package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.{MapTokenDao, ProjectDao, UserMaybePlatformId}
import com.rasterfoundry.datamodel.{MapToken, Project, Visibility}
import com.rasterfoundry.{http4s => RFHttp4s}

import cats.data._
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server._
import org.http4s.util.CaseInsensitiveString

import java.util.UUID

/** List append is slow for large lists -- but we have a list of 3 elements */
@SuppressWarnings(Array("ListAppend"))
class Authenticators(val xa: Transactor[IO])
    extends LazyLogging
    with RFHttp4s.Authenticators {

  val tokensAuthenticator =
    Kleisli[OptionT[IO, ?], Request[IO], UserMaybePlatformId](
      {
        case _ -> Root / UUIDWrapper(projectId) / "map-token" / UUIDWrapper(
              mapTokenId
            ) =>
          userFromMapToken(MapTokenDao.checkProject(projectId), mapTokenId)

        case req @ _ -> UUIDWrapper(analysisId) /: _
              :? TokenQueryParamMatcher(tokenQP)
              :? MapTokenQueryParamMatcher(mapTokenQP)
            if req.scriptName == "/tools" =>
          val authHeader: OptionT[IO, Header] =
            OptionT.fromOption(
              req.headers.get(CaseInsensitiveString("Authorization"))
            )
          checkTokenAndHeader(tokenQP, authHeader) :+
            (
              OptionT.fromOption[IO](mapTokenQP) flatMap { (mapToken: UUID) =>
                userFromMapToken(
                  MapTokenDao.checkAnalysis(analysisId),
                  mapToken
                )
              }
            ) reduce { _ orElse _ }

        case req @ _ -> UUIDWrapper(projectId) /: _
              :? TokenQueryParamMatcher(tokenQP)
              :? MapTokenQueryParamMatcher(mapTokenQP) => {
          val authHeaderO: Option[Header] =
            req.headers.get(CaseInsensitiveString("Authorization"))
          val mapTokenO = mapTokenQP orElse (req.params
            .get("mapToken") map { UUID.fromString _ })
          (authHeaderO, tokenQP, mapTokenO) match {
            case (None, None, None) =>
              userFromPublicProject(projectId)
            case _ =>
              val authHeader: OptionT[IO, Header] =
                OptionT.fromOption(authHeaderO)
              checkTokenAndHeader(tokenQP, authHeader) :+
                (
                  OptionT.fromOption[IO](mapTokenO) flatMap {
                    (mapToken: UUID) =>
                      userFromMapToken(
                        MapTokenDao.checkProject(projectId),
                        mapToken
                      )
                  }
                ) reduce { _ orElse _ }
          }
        }

        case _ =>
          OptionT.none[IO, UserMaybePlatformId]
      }
    )

  private def checkTokenAndHeader(
      tokenQP: Option[String],
      authHeader: OptionT[IO, Header]
  ): List[OptionT[IO, UserMaybePlatformId]] = {
    List(
      authHeader flatMap { (header: Header) =>
        userFromToken(header.value.replace("Bearer ", ""))
      },
      OptionT.fromOption[IO](tokenQP) flatMap { (token: String) =>
        userFromToken(token)
      }
    )
  }

  private def userFromMapToken(
      func: UUID => ConnectionIO[Option[MapToken]],
      mapTokenId: UUID
  ): OptionT[IO, UserMaybePlatformId] =
    OptionT(func(mapTokenId).transact(xa)) flatMap { mapToken =>
      OptionT(getUserFromJWTwithCache(mapToken.owner))
    }

  private def userFromPublicProject(
      id: UUID
  ): OptionT[IO, UserMaybePlatformId] =
    for {
      project <- OptionT[IO, Project](
        ProjectDao.query
          .filter(id)
          .filter(fr"tile_visibility=${Visibility.Public.toString}::visibility")
          .selectOption
          .transact(xa)
      )
      userMaybePlatformId <- OptionT(getUserFromJWTwithCache(project.owner))
    } yield userMaybePlatformId

  val tokensAuthMiddleware = AuthMiddleware(tokensAuthenticator map {
    case (user, maybePlatformId) => {
      maybePlatformId.map { platformId =>
        logger.info(s"platformId = $platformId")
      }
      user
    }
  })
}
