package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.common.ast.MapAlgebraAST
import com.rasterfoundry.common.ast.codec.MapAlgebraCodec._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.ToolRunDao
import com.rasterfoundry.database.{SceneToLayerDao}
import com.rasterfoundry.datamodel._

import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._

import java.util.UUID

class ToolStoreImplicits[HistStore](
    mosaicImplicits: MosaicImplicits[HistStore],
    xa: Transactor[IO])(implicit contextShift: ContextShift[IO])
    extends RenderableStoreImplicits(xa)
    with LazyLogging {

  import mosaicImplicits._
  implicit val tmsReification = rawMosaicTmsReification

  val mamlAdapter =
    new BacksplashMamlAdapter(mosaicImplicits, SceneToLayerDao(), xa)

  private def toolToColorRd(toolRd: RenderDefinition): RenderDefinition = {
    val scaleOpt = toolRd.scale match {
      case Continuous      => Continuous
      case Sequential      => Sequential
      case Diverging       => Diverging
      case Qualitative(fb) => Qualitative(fb)
    }

    val clipOpt = toolRd.clip match {
      case ClipNone  => ClipNone
      case ClipLeft  => ClipLeft
      case ClipRight => ClipRight
      case ClipBoth  => ClipBoth
    }

    RenderDefinition(toolRd.breakpoints, scaleOpt, clipOpt)
  }

  private def unsafeGetAST(analysisId: UUID,
                           nodeId: Option[UUID]): IO[MapAlgebraAST] =
    (for {
      executionParams <- ToolRunDao.query.filter(analysisId).select map {
        _.executionParameters
      }
    } yield {
      val decoded = executionParams.as[MapAlgebraAST] match {
        case Right(x) =>
          x
        case Left(e) =>
          logger.error(e.getMessage)
          throw BadAnalysisASTException(e.getMessage)
      }
      nodeId map {
        decoded
          .find(_)
          .getOrElse {
            throw BadAnalysisASTException(
              s"Could not find node $nodeId in analysis $analysisId")
          }
      } getOrElse { decoded }
    }).transact(xa)

  implicit val toolRunDaoStore: ToolStore[ToolRunDao] =
    new ToolStore[ToolRunDao] {

      /** Unclear what un-matching this would even be -- I think scapegoat was mad about the
        * de-sugared code? Annoying
        */
      @SuppressWarnings(Array("PartialFunctionInsteadOfMatch"))
      def read(self: ToolRunDao,
               analysisId: UUID,
               nodeId: Option[UUID]): IO[PaintableTool] =
        for {
          (expr, mdOption, params) <- unsafeGetAST(analysisId, nodeId) map {
            mamlAdapter.asMaml _
          }
        } yield {
          PaintableTool(expr, params, mdOption flatMap {
            _.renderDef map { toolToColorRd(_) }
          })
        }
    }

}
