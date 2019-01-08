package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.ToolRunDao
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.tool
import com.rasterfoundry.tool.ast.MapAlgebraAST

import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._

import java.util.UUID

class ToolStoreImplicits[HistStore: HistogramStore](
    mosaicImplicits: MosaicImplicits[HistStore],
    xa: Transactor[IO],
    mtr: MetricsRegistrator)
    extends ProjectStoreImplicits(xa, mtr) {

  import mosaicImplicits._
  implicit val tmsReification = rawMosaicTmsReification

  val mamlAdapter = new BacksplashMamlAdapter(mosaicImplicits, xa, mtr)

  private def toolToColorRd(toolRd: tool.RenderDefinition): RenderDefinition = {
    val scaleOpt = toolRd.scale match {
      case tool.Continuous      => Continuous
      case tool.Sequential      => Sequential
      case tool.Diverging       => Diverging
      case tool.Qualitative(fb) => Qualitative(fb)
    }

    val clipOpt = toolRd.clip match {
      case tool.ClipNone  => ClipNone
      case tool.ClipLeft  => ClipLeft
      case tool.ClipRight => ClipRight
      case tool.ClipBoth  => ClipBoth
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
      val decoded = executionParams.as[MapAlgebraAST].toOption getOrElse {
        throw MetadataException(s"Could not decode AST for $analysisId")
      }
      nodeId map {
        decoded
          .find(_)
          .getOrElse {
            throw MetadataException(
              s"Node $nodeId missing from AST $analysisId")
          }
      } getOrElse { decoded }
    }).transact(xa)

  implicit val toolRunDaoStore: ToolStore[ToolRunDao] =
    new ToolStore[ToolRunDao] {
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
