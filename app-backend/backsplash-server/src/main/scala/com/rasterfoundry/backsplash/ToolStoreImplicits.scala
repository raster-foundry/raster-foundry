package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.database.ToolRunDao
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.tool

import doobie._
import doobie.implicits._
import cats.effect.IO

import java.util.UUID

class ToolStoreImplicits(mosaicImplicits: MosaicImplicits) {
  import mosaicImplicits._
  val mamlAdapter = new BacksplashMamlAdapter(mosaicImplicits)

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

  implicit val toolRunDaoStore: ToolStore[ToolRunDao] =
    new ToolStore[ToolRunDao] {
      def read(self: ToolRunDao,
               analysisId: UUID,
               nodeId: Option[UUID]): IO[PaintableTool] =
        for {
          (expr, mdOption, params) <- ToolRunDao
            .unsafeGetAST(analysisId, nodeId)
            .transact(RFTransactor.xa) map {
            mamlAdapter.asMaml _
          }
        } yield {
          PaintableTool(expr, params, mdOption flatMap {
            _.renderDef map { toolToColorRd(_) }
          })
        }
    }

}
