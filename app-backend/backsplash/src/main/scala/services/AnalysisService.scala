package com.rasterfoundry.backsplash.analysis

import java.security.InvalidParameterException
import java.util.UUID

import cats.data.Validated._
import cats.effect._
import cats.implicits._
import com.azavea.maml.eval.BufferingInterpreter
import com.rasterfoundry.authentication.Authentication
import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.maml.BacksplashMamlAdapter
import com.rasterfoundry.backsplash.parameters.Parameters._
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.datamodel.User
import com.rasterfoundry.database.ToolRunDao
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.tool.ast.{MapAlgebraAST, _}
import doobie.implicits._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.server._
import org.http4s.{MediaType, _}
import org.http4s.dsl._
import org.http4s.headers._

import scala.util._

class AnalysisService(
    interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit timer: Timer[IO],
  cs: ContextShift[IO],
  H: HttpErrorHandler[IO, BacksplashException],
  ForeignError: HttpErrorHandler[IO, Throwable])
    extends Http4sDsl[IO]
    with RollbarNotifier {

  implicit val xa = RFTransactor.xa

  object VoidCacheQueryParamMatcher
      extends QueryParamDecoderMatcher[Boolean]("voidCache")

  implicit class MapAlgebraAstConversion(val rfmlAst: MapAlgebraAST)
      extends BacksplashMamlAdapter

  val service: AuthedService[User, IO] =
    H.handle {
      ForeignError.handle {
        AuthedService {
          case GET -> Root / UUIDWrapper(analysisId) / histogram
                :? NodeQueryParamMatcher(node)
                :? VoidCacheQueryParamMatcher(void) as user => {

            ???
          }

          case GET -> Root / UUIDWrapper(analysisId) / IntVar(z) / IntVar(x) / IntVar(
                y)
                :? NodeQueryParamMatcher(node) as user => {

            logger.info(s"Requesting Analysis: ${analysisId}")
            val tr = ToolRunDao.query.filter(analysisId).select.transact(xa)

            val mapAlgebraAST = tr map { toolRun =>
              logger.info(s"Getting AST")
              val ast = toolRun.executionParameters
                .as[MapAlgebraAST]
                .right
                .toOption
                .getOrElse(throw MetadataException(
                  s"Could not decode AST ${analysisId} from database"))
              ast
                .find(UUID.fromString(node))
                .getOrElse(throw MetadataException(
                  s"Node ${node} missing from in AST ${analysisId}"))
            }

            logger.debug(s"AST: ${mapAlgebraAST}")
            mapAlgebraAST.flatMap { ast =>
              val (exp, mdOption, params) = ast.asMaml
              val layerEval =
                LayerTms.apply(IO.pure(exp), IO.pure(params), interpreter)
              val tileIO = layerEval(z, x, y)
              tileIO.attempt flatMap {
                case Left(error) => ???
                case Right(Valid(tile)) => {
                  val colorMap = for {
                    md <- mdOption
                    renderDef <- md.renderDef
                  } yield renderDef

                  colorMap match {
                    case Some(rd) => {
                      logger.debug(s"Using Render Definition: ${rd}")
                      Ok(tile.renderPng(rd).bytes,
                         `Content-Type`(MediaType.image.png))
                    }
                    case _ => {
                      logger.debug(s"Using Default Color Ramp: Viridis")
                      Ok(tile.renderPng(ColorRamps.Viridis).bytes,
                         `Content-Type`(MediaType.image.png))
                    }
                  }
                }
                case Right(Invalid(e)) => {
                  BadRequest(e.toString)
                }
              }
            }
          }
        }
      }
    }
}
