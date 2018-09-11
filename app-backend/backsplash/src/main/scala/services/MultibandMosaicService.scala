package com.azavea.rf.backsplash.services

import com.azavea.rf.authentication.Authentication
import com.azavea.rf.backsplash.parameters.PathParameters._
import com.azavea.rf.backsplash.nodes.ProjectNode
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.database.{ProjectDao, SceneToProjectDao, UserDao}
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel._
import com.azavea.maml.eval.BufferingInterpreter

import cats._
import cats.data._
import cats.data.Validated._
import cats.effect.{IO, Timer}
import cats.implicits._
import cats.syntax._
import doobie.implicits._
import geotrellis.proj4.WebMercator
import geotrellis.raster.{IntArrayTile, MultibandTile, Raster, Tile}
import geotrellis.raster.render.ColorRamps
import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.maml._
import geotrellis.server.core.maml.reification.MamlTmsReification
import geotrellis.vector.{Projected, Polygon}
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server._

import java.net.URI
import java.util.UUID

class MultibandMosaicService(
  interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit t: Timer[IO]) extends Http4sDsl[IO] with RollbarNotifier with Authentication {

  implicit val xa = RFTransactor.xa

  // final val eval = MamlTms.curried(RasterVar("identity"), interpreter)
  final val eval = MamlTms.identity[ProjectNode](interpreter)

  object TokenQueryParamMatcher extends QueryParamDecoderMatcher[String]("token")
  object RedBandOptionalQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("redBand")
  object GreenBandOptionalQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("greenBand")
  object BlueBandOptionalQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("blueBand")

  val service: HttpService[IO] =
    HttpService {
      case GET -> Root / UUIDWrapper(projectId) / IntVar(z) / IntVar(x) / IntVar(y)
          :? TokenQueryParamMatcher(token)
          :? RedBandOptionalQueryParamMatcher(redOverride)
          :? GreenBandOptionalQueryParamMatcher(greenOverride)
          :? BlueBandOptionalQueryParamMatcher(blueOverride) =>
        val authIO: IO[Either[Throwable, Boolean]] = (
          for {
            user <- verifyJWT(token) match {
              case Right((_, jwtClaims)) => UserDao.unsafeGetUserById(jwtClaims.getStringClaim("sub")).transact(xa)
              case Left(e) => IO(throw new Exception("Failed to validate user from JWT"))
            }
            authorized <- ProjectDao.query.authorized(user, ObjectType.Project, projectId, ActionType.View).transact(xa)
          } yield authorized
        ).attempt
        val projectNode = (redOverride, greenOverride, blueOverride).tupled match {
          case Some((red: Int, green: Int, blue: Int)) => ProjectNode(projectId, Some(red), Some(green), Some(blue))
          case _ => ProjectNode(projectId)
        }
        val paramMap = Map("identity" -> projectNode)
        val result = eval(paramMap, z, x, y).attempt
        IO.shift(t) *> authIO flatMap {
          case authed =>
            authed match {
              case (Left(_) | Right(false)) =>
                Forbidden("get outta here")
              case Right(true) =>
                result flatMap {
                  case result =>
                    result match {
                      case Right(Valid(tile)) =>
                        Ok(tile.renderPng.bytes)
                      case Right(Invalid(e)) =>
                        BadRequest(e.toString)
                      case Left(e) =>
                        BadRequest(e.getMessage)
                    }
                }
            }
        }
    }
}
