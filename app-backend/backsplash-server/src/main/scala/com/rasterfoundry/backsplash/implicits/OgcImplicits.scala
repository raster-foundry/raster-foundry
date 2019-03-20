package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.{BacksplashMosaic, OgcStore, ProjectStore, StyleModels}
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.common.datamodel.{ColorComposite, ProjectLayer}
import com.rasterfoundry.database.{
  ProjectDao,
  ProjectLayerDao,
  ProjectLayerDatasourcesDao
}

import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.server.ogc.{OgcSource, SimpleSource, StyleModel}
import geotrellis.server.ogc.ows._
import geotrellis.server.ogc.wcs.WcsModel
import geotrellis.server.ogc.wms.{WmsModel, WmsParentLayerMeta}
import geotrellis.server.ogc.wms.wmsScope
import _root_.io.circe.syntax._
import opengis.wms.{Name, OnlineResource, Service}

import java.util.UUID

class OgcImplicits[P: ProjectStore](layers: P, xa: Transactor[IO])
    extends ToProjectStoreOps {

  private def compositesToStyleModels(
      composites: Map[String, ColorComposite]): List[StyleModel] =
    composites.values map { StyleModels.fromColorComposite _ }

  private def datasourcesToStyleModels(
    datasources: List[Datasource]): List[StyleModel] = {
    val bands: List[Band] = datasources flatMap {
      _.bands.as[List[Band]].toOption getOrElse Nil
    }
    val shortest = bands.minBy(_.length)
    shortest.zipWithIndex map {
      case (_, i) =>
        StyleModels.fromSingleBandOptions(
          SingleBandOptions.Params(
            i,
            BandDataType.Sequential,
            0,
            () asJson, // colorScheme -- need to construct this from Viridis somehow
            "left"
          )
        )
    }
  }

  private def getStyles(projectLayerId: UUID): IO[List[StyleModel]] =
    ProjectLayerDatasourcesDao
      .listProjectLayerDatasources(projectLayerId)
      .transact(xa) map { datasources =>
      (datasources flatMap { datasource =>
        compositesToStyleModels(datasource.composites)
      }) ++ datasourcesToStyleModels(datasources)
    }

  private def projectLayerToSimpleSource(
      projectLayer: ProjectLayer): IO[SimpleSource] =
    for {
      rsm <- BacksplashMosaic.toRasterSource(
        layers.read(projectLayer.id, None, None, None))
      styleModels <- getStyles(projectLayer.id)
    } yield {
      SimpleSource(
        projectLayer.name,
        projectLayer.id.toString,
        rsm,
        styleModels
      )
    }

  private def getSources(projectId: UUID): IO[List[OgcSource]] =
    for {
      projectLayers <- ProjectLayerDao
        .listProjectLayersWithImagery(projectId)
        .transact(xa)
      sources <- projectLayers traverse { projectLayerToSimpleSource _ }
    } yield sources

  implicit val projectOgcStore: OgcStore[ProjectDao] =
    new OgcStore[ProjectDao] {
      def getWcsModel(self: ProjectDao, id: UUID): IO[WcsModel] =
        for {
          sources <- getSources(id)
        } yield {
          val serviceMeta = ServiceMetadata(
            Identification("", "", Nil, Nil, None, Nil),
            Provider("", None, None)
          )
          WcsModel(serviceMeta, sources)
        }

      def getWmsModel(self: ProjectDao, id: UUID): IO[WmsModel] =
        for {
          sources <- getSources(id)
          service <- getWmsServiceMetadata(self, id)
        } yield {
          val parentLayerMeta = WmsParentLayerMeta(None,
                                                   "Raster Foundry WMS Layer",
                                                   None,
                                                   List(LatLng, WebMercator))
          WmsModel(service, parentLayerMeta, sources)
        }

      def getWmsServiceMetadata(self: ProjectDao, id: UUID): IO[Service] =
        for {
          project <- ProjectDao.unsafeGetProjectById(id).transact(xa)
        } yield {
          Service(
            Name.fromString("WMS", wmsScope),
            project.name,
            None, // ???
            None, // keyword list
            OnlineResource(Map.empty), // online resource
            None, // contact information
            None, // ???
            None, // ???
            None, // ???
            None, // ???
            None // ???
          )
        }

    }
}
