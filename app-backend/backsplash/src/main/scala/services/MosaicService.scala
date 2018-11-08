package com.rasterfoundry.backsplash.services

import com.azavea.maml.error.Interpreted
import com.azavea.maml.eval.BufferingInterpreter
import com.rasterfoundry.authentication.Authentication
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.io.Histogram
import com.rasterfoundry.backsplash.nodes.ProjectNode
import com.rasterfoundry.backsplash.parameters.Parameters._
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{ProjectDao, UserDao}
import com.rasterfoundry.datamodel._

import cats.data.Validated._
import cats.data._
import cats.effect._
import cats.implicits._
import doobie.implicits._
import geotrellis.raster.{CellSize, Tile}
import geotrellis.vector.{Projected, Polygon, Extent}
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import geotrellis.server._
import geotrellis.server.cog.util.CogUtils
import org.http4s._
import org.http4s.dsl._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server._
import org.http4s.util.CaseInsensitiveString

import java.net.URI
import java.util.UUID
import java.util.Base64

class MosaicService(
    interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit timer: Timer[IO],
  cs: ContextShift[IO],
  H: HttpErrorHandler[IO, BacksplashException],
  ForeignError: HttpErrorHandler[IO, Throwable])
    extends Http4sDsl[IO]
    with RollbarNotifier {

  implicit val xa = RFTransactor.xa

  val service: AuthedService[User, IO] =
    H.handle {
      ForeignError.handle {
        AuthedService {
          case req @ GET -> Root / UUIDWrapper(projectId) / IntVar(z) / IntVar(
                x) / IntVar(y) / _
                :? RedBandOptionalQueryParamMatcher(redOverride)
                :? GreenBandOptionalQueryParamMatcher(greenOverride)
                :? BlueBandOptionalQueryParamMatcher(blueOverride)
                :? TagOptionalQueryParamMatcher(tag) as user =>
            val authorizationF =
              ProjectDao
                .authorized(user,
                            ObjectType.Project,
                            projectId,
                            ActionType.View)
                .transact(xa) map { authResult =>
                if (!authResult)
                  throw NotAuthorizedException(
                    s"User ${user.id} not authorized to view project $projectId")
                else
                  authResult
              }

            def getTileResult(project: Project): IO[Interpreted[Tile]] = {
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
              val eval = LayerTms.identity[ProjectNode](projectNode)
              eval(z, x, y)
            }

            for {
              authed <- authorizationF
              project <- ProjectDao.unsafeGetProjectById(projectId).transact(xa)
              result <- getTileResult(project)
              resp <- result match {
                case Valid(tile) =>
                  Ok(tile.renderPng.bytes, `Content-Type`(MediaType.image.png))
                case Invalid(e) =>
                  BadRequest(e.toString)
              }
            } yield resp

          case GET -> Root / UUIDWrapper(projectId) / "histogram" / _
                :? RedBandOptionalQueryParamMatcher(redOverride)
                :? GreenBandOptionalQueryParamMatcher(greenOverride)
                :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
            for {
              histograms <- Histogram.getRGBProjectHistogram(projectId,
                                                             None,
                                                             None,
                                                             None,
                                                             None,
                                                             List.empty[UUID])
              resp <- Ok((histograms map { _.binCounts.toMap } asJson).noSpaces)
            } yield resp

          case authedReq @ POST -> Root / UUIDWrapper(projectId) / "histogram" / _
                :? RedBandOptionalQueryParamMatcher(redOverride)
                :? GreenBandOptionalQueryParamMatcher(greenOverride)
                :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
            // Compile to a byte array, decode that as a string, and do something with the results
            authedReq.req.body.compile.to[Array] flatMap { uuids =>
              decode[List[UUID]](
                uuids map { _.toChar } mkString
              ) match {
                case Right(uuids) =>
                  logger.info(s"How many uuids: ${uuids.length}")
                  for {
                    histograms <- Histogram.getRGBProjectHistogram(
                      projectId,
                      None,
                      redOverride,
                      greenOverride,
                      blueOverride,
                      uuids)
                    resp <- Ok(
                      (histograms map { _.binCounts.toMap } asJson).noSpaces)
                  } yield resp
                case _ =>
                  BadRequest("Could not decode body as sequence of UUIDs")
              }
            }

          case authedReq @ GET -> Root / UUIDWrapper(projectId) / "export" / _
                :? ExtentQueryParamMatcher(extent)
                :? ZoomQueryParamMatcher(zoom)
                :? RedBandOptionalQueryParamMatcher(redOverride)
                :? GreenBandOptionalQueryParamMatcher(greenOverride)
                :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
            val authorizationF =
              ProjectDao
                .authorized(user,
                            ObjectType.Project,
                            projectId,
                            ActionType.View)
                .transact(xa) map { authResult =>
                if (!authResult)
                  throw NotAuthorizedException(
                    s"User ${user.id} not authorized to view project $projectId")
                else
                  authResult
              }
            def getTileResult(
                project: Project,
                cellSize: CellSize,
                projectedExtent: Extent): IO[Interpreted[Tile]] = {
              val projectNode = ProjectNode(
                project.id,
                redOverride,
                greenOverride,
                blueOverride,
                project.isSingleBand,
                project.singleBandOptions
              )
              val eval = LayerExtent.identity[ProjectNode](projectNode)
              eval(projectedExtent, cellSize)
            }
            for {
              authed <- authorizationF
              cellSize = CogUtils.tmsLevels(zoom).cellSize
              project <- ProjectDao.unsafeGetProjectById(projectId).transact(xa)
              result <- getTileResult(project, cellSize, extent)
              resp <- result match {
                case Valid(tile) =>
                  Ok(tile.renderPng.bytes, `Content-Type`(MediaType.image.png))
                case Invalid(e) =>
                  BadRequest(e.toString)
              }
            } yield resp
        }
      }
    }
}
