package com.azavea.rf.backsplash.analysis

import java.security.InvalidParameterException
import java.util.UUID

import cats.data.Validated._
import cats.effect.{IO, Timer}
import cats.implicits._
import com.azavea.maml.eval.BufferingInterpreter
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.backsplash._
import com.azavea.rf.backsplash.error._
import com.azavea.rf.backsplash.maml.BacksplashMamlAdapter
import com.azavea.rf.backsplash.parameters.PathParameters._
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.datamodel.User
import com.azavea.rf.database.ToolRunDao
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.tool.ast.{MapAlgebraAST, _}
import doobie.implicits._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.server.core.maml._
import org.http4s.{MediaType, _}
import org.http4s.dsl._
import org.http4s.headers._

import scala.util._

class AnalysisService(
    interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit timer: Timer[IO])
    extends Http4sDsl[IO]
    with ErrorHandling {

  implicit val xa = RFTransactor.xa

  object NodeQueryParamMatcher extends QueryParamDecoderMatcher[String]("node")

  object VoidCacheQueryParamMatcher
      extends QueryParamDecoderMatcher[Boolean]("voidCache")

  implicit class MapAlgebraAstConversion(val rfmlAst: MapAlgebraAST)
      extends BacksplashMamlAdapter

  val service: AuthedService[User, IO] =
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

        val mapAlgebraAST = tr.flatMap { toolRun =>
          logger.info(s"Getting AST")
          val ast = toolRun.executionParameters
            .as[MapAlgebraAST]
            .right
            .toOption
            .getOrElse(throw MetadataError(
              s"Could not decode AST ${analysisId} from database"))
          IO.pure(
            ast
              .find(UUID.fromString(node))
              .getOrElse(throw MetadataError(
                s"Node ${node} missing from in AST ${analysisId}")))
        }

        logger.debug(s"AST: ${mapAlgebraAST}")
        val respIO = mapAlgebraAST.flatMap { ast =>
          val (exp, mdOption, params) = ast.asMaml
          val mamlEval =
            MamlTms.apply(IO.pure(exp), IO.pure(params), interpreter)
          val tileIO = mamlEval(z, x, y)
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
                     `Content-Type`(MediaType.`image/png`))
                }
                case _ => {
                  logger.debug(s"Using Default Color Ramp: Viridis")
                  Ok(tile.renderPng(ColorRamps.Viridis).bytes,
                     `Content-Type`(MediaType.`image/png`))
                }
              }
            }
            case Right(Invalid(e)) => {
              BadRequest(e.toString)
            }
          }
        }

        respIO.handleErrorWith(handleErrors _)
      }
    }
}
