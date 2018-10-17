package com.rasterfoundry.backsplash.services

import com.azavea.maml.error.Interpreted
import com.azavea.maml.eval.BufferingInterpreter
import com.rasterfoundry.authentication.Authentication
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.io.Histogram
import com.rasterfoundry.backsplash.nodes.ProjectNode
import com.rasterfoundry.backsplash.parameters.PathParameters._
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{ProjectDao, UserDao}
import com.rasterfoundry.datamodel._

import cats.data.Validated._
import cats.data._
import cats.effect._
import cats.implicits._
import doobie.implicits._
import geotrellis.raster.Tile
import geotrellis.server.core.maml._
import geotrellis.server.core.maml.reification.MamlTmsReification
import geotrellis.vector.{Projected, Polygon}
import io.circe._
import io.circe.syntax._
import io.circe.parser._
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
)(implicit timer: Timer[IO])
    extends Http4sDsl[IO]
    with ErrorHandling {

  implicit val xa = RFTransactor.xa

  // final val eval = MamlTms.curried(RasterVar("identity"), interpreter)
  final val eval = MamlTms.identity[ProjectNode](interpreter)

  object RedBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("redBand")
  object GreenBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("greenBand")
  object BlueBandOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Int]("blueBand")
  object TagOptionalQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[String]("tag")

  val service: AuthedService[User, IO] =
    AuthedService {
      case req @ GET -> Root / UUIDWrapper(projectId) / IntVar(z) / IntVar(x) / IntVar(
            y) / _
            :? RedBandOptionalQueryParamMatcher(redOverride)
            :? GreenBandOptionalQueryParamMatcher(greenOverride)
            :? BlueBandOptionalQueryParamMatcher(blueOverride)
            :? TagOptionalQueryParamMatcher(tag) as user =>
        val authorizationIO =
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.View)
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
          val paramMap = Map("identity" -> projectNode)
          eval(paramMap, z, x, y)
        }

        val respIO = for {
          authed <- authorizationIO
          project <- ProjectDao.unsafeGetProjectById(projectId).transact(xa)
          result <- getTileResult(project)
          resp <- result match {
            case Valid(tile) =>
              Ok(tile.renderPng.bytes, `Content-Type`(MediaType.`image/png`))
            case Invalid(e) =>
              BadRequest(e.toString)
          }
        } yield resp
        respIO.handleErrorWith(handleErrors _)

      case GET -> Root / UUIDWrapper(projectId) / "histogram" / _
            :? RedBandOptionalQueryParamMatcher(redOverride)
            :? GreenBandOptionalQueryParamMatcher(greenOverride)
            :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
        val respIO = for {
          histograms <- Histogram.getRGBProjectHistogram(projectId,
                                                         None,
                                                         None,
                                                         None,
                                                         None,
                                                         List.empty[UUID])
          resp <- Ok((histograms map { _.binCounts.toMap } asJson).noSpaces)
        } yield resp
        respIO handleErrorWith { handleErrors _ }

      case authedReq @ POST -> Root / UUIDWrapper(projectId) / "histogram" / _
            :? RedBandOptionalQueryParamMatcher(redOverride)
            :? GreenBandOptionalQueryParamMatcher(greenOverride)
            :? BlueBandOptionalQueryParamMatcher(blueOverride) as user => {
        // Compile to a byte array, decode that as a string, and do something with the results
        authedReq.req.body.compile.to[Array] flatMap { uuids =>
          decode[List[UUID]](
            uuids map { _.toChar } mkString
          ) match {
            case Right(uuids) =>
              logger.info(s"How many uuids: ${uuids.length}")
              for {
                histograms <- Histogram.getRGBProjectHistogram(projectId,
                                                               None,
                                                               redOverride,
                                                               greenOverride,
                                                               blueOverride,
                                                               uuids)
                resp <- Ok(
                  (histograms map { _.binCounts.toMap } asJson).noSpaces)
              } yield resp
            case _ => BadRequest("Could not decode body as sequence of UUIDs")
          }
        } handleErrorWith (handleErrors _)
      }
    }
}
