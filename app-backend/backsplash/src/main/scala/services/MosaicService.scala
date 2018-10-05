package com.azavea.rf.backsplash.services

import cats.data.Validated._
import cats.data._
import cats.effect.{IO, Timer}
import cats.implicits._
import com.azavea.maml.error.Interpreted
import com.azavea.maml.eval.BufferingInterpreter
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.backsplash.nodes.ProjectNode
import com.azavea.rf.backsplash.parameters.PathParameters._
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.database.{ProjectDao, UserDao}
import com.azavea.rf.datamodel._
import doobie.implicits._
import geotrellis.raster.Tile
import geotrellis.server.core.maml._
import org.http4s._
import org.http4s.dsl._

class MosaicService(
    interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit t: Timer[IO])
    extends Http4sDsl[IO]
    with RollbarNotifier
    with Authentication {

  implicit val xa = RFTransactor.xa

  // final val eval = MamlTms.curried(RasterVar("identity"), interpreter)
  final val eval = MamlTms.identity[ProjectNode](interpreter)

  object TokenQueryParamMatcher
      extends QueryParamDecoderMatcher[String]("token")
  object RedBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("redBand")
  object GreenBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("greenBand")
  object BlueBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("blueBand")

  val service: HttpService[IO] =
    HttpService {
      case GET -> Root / UUIDWrapper(projectId) / IntVar(z) / IntVar(x) / IntVar(
            y)
            :? TokenQueryParamMatcher(token)
            :? RedBandOptionalQueryParamMatcher(redOverride)
            :? GreenBandOptionalQueryParamMatcher(greenOverride)
            :? BlueBandOptionalQueryParamMatcher(blueOverride) =>
        val authIO: EitherT[IO, Throwable, Boolean] =
          for {
            user <- (
              verifyJWT(token) match {
                case Right((_, jwtClaims)) =>
                  EitherT.liftF(
                    UserDao
                      .unsafeGetUserById(jwtClaims.getStringClaim("sub"))
                      .transact(xa)
                  )
                case Left(e) =>
                  EitherT.leftT[IO, User](
                    new Exception("Failed to validate user from JWT")
                  )
              }
            )
            authorized <- EitherT.liftF(
              ProjectDao
                .authorized(user,
                            ObjectType.Project,
                            projectId,
                            ActionType.View)
                .transact(xa)
            )
          } yield authorized

        def getTileResult(
            project: Project): EitherT[IO, Throwable, Interpreted[Tile]] = {
          val projectNode =
            (redOverride, greenOverride, blueOverride).tupled match {
              case Some((red: Int, green: Int, blue: Int)) =>
                ProjectNode(projectId,
                            Some(red),
                            Some(green),
                            Some(blue),
                            project.isSingleBand,
                            project.singleBandOptions,
                            false)
              case _ =>
                ProjectNode(projectId,
                            None,
                            None,
                            None,
                            project.isSingleBand,
                            project.singleBandOptions,
                            false)
            }
          val paramMap = Map("identity" -> projectNode)
          EitherT(IO.shift(t) *> eval(paramMap, z, x, y).attempt)
        }

        IO.shift(t) *> (
          for {
            authed <- authIO
            project <- EitherT(
              IO.shift(t) *> ProjectDao
                .unsafeGetProjectById(projectId)
                .transact(xa)
                .attempt)
            result <- getTileResult(project)
          } yield {
            result match {
              case Valid(tile) =>
                Ok(tile.renderPng.bytes)
              case Invalid(e) =>
                BadRequest(e.toString)
            }
          }
        ).value flatMap {
          case Left(e)     => BadRequest(e.getMessage)
          case Right(resp) => resp
        }
    }
}
