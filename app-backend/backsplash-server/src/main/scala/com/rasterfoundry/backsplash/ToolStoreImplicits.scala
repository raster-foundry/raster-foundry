package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.ToolRunDao
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.ast.MapAlgebraAST
import com.rasterfoundry.common.ast.codec.MapAlgebraCodec._

import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._

import java.util.UUID

class ToolStoreImplicits[HistStore: HistogramStore](
    mosaicImplicits: MosaicImplicits[HistStore],
    xa: Transactor[IO])
    extends ProjectStoreImplicits(xa) {

  import mosaicImplicits._
  implicit val tmsReification = rawMosaicTmsReification

  val mamlAdapter = new BacksplashMamlAdapter(mosaicImplicits, xa)

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
