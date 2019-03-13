package com.rasterfoundry.backsplash.implicits

import com.rasterfoundry.backsplash.{OgcStore, ProjectStore}
import com.rasterfoundry.database.ProjectDao

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Transactor
import doobie.implicits._
import geotrellis.server.ogc.conf._

import java.util.UUID

class OgcImplicits[P: ProjectStore](xa: Transactor[IO]) {
  // Question:
  // I'm constructing an OgcSourceConf for a project
  // Projects have layers
  // layers have scenes
  // I only get ONE RasterSourceConf for this OgcSourceConf
  // How do I express the plurality? Where do the layers come in?
  // It _seems_ like a RasterSourceConf <=> a layer
  implicit val projectOgcStore: OgcStore[ProjectDao] =
    new OgcStore[ProjectDao] {
      def getConfig(self: ProjectDao, id: UUID): IO[OgcSourceConf] =
        for {
          project <- ProjectDao.unsafeGetProjectById(id).transact(xa)
        } yield {
          SimpleSourceConf(
            project.name,
            project.name,
            Mosaic(
              ??? : NonEmptyList[RasterSourceConf]
            ),
            Nil
          )
        }
    }
}
