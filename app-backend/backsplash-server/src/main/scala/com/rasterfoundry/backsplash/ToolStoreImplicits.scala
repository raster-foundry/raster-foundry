package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.database.ToolRunDao
import com.rasterfoundry.database.util.RFTransactor

import doobie._
import doobie.implicits._
import cats.effect.IO

import java.util.UUID

class ToolStoreImplicits(mosaicImplicits: MosaicImplicits) {
  import mosaicImplicits._
  val mamlAdapter = new BacksplashMamlAdapter(mosaicImplicits)

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
          PaintableTool(expr, identity, params)
        }
    }

}
