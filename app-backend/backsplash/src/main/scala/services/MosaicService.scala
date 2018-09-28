package com.azavea.rf.backsplash.services

import cats.data.Validated._
import cats.data._
import cats.effect.{IO, Timer}
import cats.implicits._
import com.azavea.maml.error.Interpreted
import com.azavea.maml.eval.BufferingInterpreter
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.backsplash.error._
import com.azavea.rf.backsplash.parameters.PathParameters._
import com.azavea.rf.backsplash.nodes.ProjectNode
import com.azavea.rf.backsplash.parameters.PathParameters._
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.database.{ProjectDao, UserDao}
import com.azavea.rf.datamodel._
import com.azavea.maml.error.Interpreted
import com.azavea.maml.eval.BufferingInterpreter

import cats._
import cats.data._
import cats.data.Validated._
import cats.effect.{Timer, IO}
import cats.implicits._
import cats.syntax._
import doobie.implicits._
import geotrellis.raster.Tile
import geotrellis.server.core.maml._
import org.http4s._
import org.http4s.dsl._

class MosaicService(
    interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit timer: Timer[IO]) extends Http4sDsl[IO] with RollbarNotifier {

  implicit val xa = RFTransactor.xa

  // final val eval = MamlTms.curried(RasterVar("identity"), interpreter)
  final val eval = MamlTms.identity[ProjectNode](interpreter)

  object RedBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("redBand")
  object GreenBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("greenBand")
  object BlueBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("blueBand")

  val service: AuthedService[User, IO] =
    AuthedService {
      case req @ GET -> Root / UUIDWrapper(projectId) / IntVar(z) / IntVar(x) / IntVar(
            y)
            :? RedBandOptionalQueryParamMatcher(redOverride)
            :? GreenBandOptionalQueryParamMatcher(greenOverride)
            :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
        val authorizationIO =
          ProjectDao
            .authorized(user,
                        ObjectType.Project,
                        projectId,
                        ActionType.View)
            .transact(xa) map { authResult => 
              if (!authResult)
                throw NotAuthorized(s"User ${user.id} not authorized to view project $projectId")
              else
                authResult
            }

        def getTileResult(
            project: Project): IO[Interpreted[Tile]] = {
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
          eval(paramMap, z, x, y)
        }

        for {
          authed <- authorizationIO
          project <- ProjectDao.unsafeGetProjectById(projectId).transact(xa)
          result <- getTileResult(project)
        } yield {
          result match {
            case Valid(tile) =>
              ??? // Ok(tile.renderPng.bytes)
            case Invalid(e) =>
              ??? // BadRequest(e.toString)
          }
        }
    }
}
