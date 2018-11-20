package com.rasterfoundry.backsplash.analysis

import java.util.UUID
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import geotrellis.raster.histogram._
import geotrellis.raster.io._
import spray.json._
import DefaultJsonProtocol._
import org.http4s.circe._

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

import com.rasterfoundry.tool.ast._

import geotrellis.raster.summary.Statistics
import geotrellis.raster.mapalgebra.focal._
import cats.syntax.either._

import scala.util.Try

class AnalysisService(
    interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit timer: Timer[IO],
  cs: ContextShift[IO],
  H: HttpErrorHandler[IO, BacksplashException],
  ForeignError: HttpErrorHandler[IO, Throwable])
    extends Http4sDsl[IO]
    with RollbarNotifier {

  implicit val xa = RFTransactor.xa

  implicit val sprayJsonEncoder: Encoder[JsValue] = new Encoder[JsValue] {
    final def apply(jsvalue: JsValue): Json =
      parse(jsvalue.compactPrint) match {
        case Right(success) => success
        case Left(fail)     => throw fail
      }
  }
  implicit val histogramDecoder: Decoder[Histogram[Double]] =
    Decoder[Json].map { js =>
      js.noSpaces.parseJson.convertTo[Histogram[Double]]
    }

  implicit val histogramEncoder: Encoder[Histogram[Double]] =
    new Encoder[Histogram[Double]] {
      final def apply(hist: Histogram[Double]): Json = hist.toJson.asJson
    }

  implicit class MapAlgebraAstConversion(val rfmlAst: MapAlgebraAST)
      extends BacksplashMamlAdapter

  val service: AuthedService[User, IO] =
    H.handle {
      ForeignError.handle {
        AuthedService {
          case GET -> Root / UUIDWrapper(analysisId) / "histogram"
                :? NodeQueryParamMatcher(node)
                :? VoidCacheQueryParamMatcher(void) as user => {

            logger.info(
              s"Requesting Analysis histogram. Analysis=${analysisId}, node=${node}")

            for {
              toolRun <- ToolRunDao.query.filter(analysisId).select.transact(xa)
              mapAlgebraAST = {
                val decodedAst = toolRun.executionParameters
                  .as[MapAlgebraAST]
                  .right
                  .toOption
                  .getOrElse(throw new BadAnalysisASTException(
                    s"Could not decode AST ${analysisId} from database"))
                node map { nodeId =>
                  decodedAst
                    .find(UUID.fromString(nodeId))
                    .getOrElse(throw BadAnalysisASTException(
                      s"Node ${nodeId} missing from in AST ${analysisId}"))
                } getOrElse { decodedAst }
              }
              (exp, mdOption, params) = mapAlgebraAST.asMaml
              result <- LayerHistogram.apply(IO.pure(exp),
                                             IO.pure(params),
                                             interpreter,
                                             4096)
              resp <- result match {
                case Valid(h) =>
                  logger.debug(s"Generated histogram: ${h.asJson}")
                  Ok(h.asJson)
                case Invalid(e) =>
                  logger.warn(e.toList.toString)
                  BadRequest(e.asJson)
              }
            } yield { resp }
          }

          case GET -> Root / UUIDWrapper(analysisId) / "raw" / _
                :? ExtentQueryParamMatcher(extent)
                :? ZoomQueryParamMatcher(zoom)
                :? NodeQueryParamMatcher(node) as user =>
            Ok("good job")

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
              node map { nodeId =>
                ast
                  .find(UUID.fromString(nodeId))
                  .getOrElse(throw BadAnalysisASTException(
                    s"Node ${nodeId} missing from in AST ${analysisId}"))
              } getOrElse { ast }
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
