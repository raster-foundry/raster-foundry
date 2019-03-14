package com.rasterfoundry.backsplash.implicits

import com.rasterfoundry.backsplash.{BacksplashMosaic, OgcStore, ProjectStore}
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.database.{ProjectDao, ProjectLayerDao}

import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._
import geotrellis.server.ogc.{RasterSourcesModel, SimpleSource}

import java.util.UUID

class OgcImplicits[P: ProjectStore](layers: P, xa: Transactor[IO])
    extends ToProjectStoreOps {
  implicit val projectOgcStore: OgcStore[ProjectDao] =
    new OgcStore[ProjectDao] {
      def getModel(self: ProjectDao, id: UUID): IO[RasterSourcesModel] =
        for {
          projectLayers <- ProjectLayerDao
            .listProjectLayersForProjectQ(id)
            .list
            .transact(xa)
          sources <- projectLayers traverse { projectLayer =>
            BacksplashMosaic.toRasterSource(
              layers.read(projectLayer.id, None, None, None)) map {
              SimpleSource(
                projectLayer.id.toString,
                projectLayer.name,
                _,
                Nil
              )
            }
          }
        } yield RasterSourcesModel(sources toSeq)
    }
}
