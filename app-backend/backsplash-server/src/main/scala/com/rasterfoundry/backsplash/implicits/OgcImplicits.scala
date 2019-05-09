package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.{BacksplashMosaic, OgcStore, ProjectStore}
import com.rasterfoundry.backsplash.color.{OgcStyles, SingleBandOptions}
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.datamodel.{ColorComposite, ProjectLayer}
import com.rasterfoundry.database.{
  LayerAttributeDao,
  ProjectDao,
  ProjectLayerDao,
  ProjectLayerDatasourcesDao
}

import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._
import geotrellis.proj4.{LatLng, WebMercator, CRS}
import geotrellis.raster.histogram.Histogram
import geotrellis.server.ogc.{OgcSource, SimpleSource, OgcStyle}
import geotrellis.server.ogc.ows._
import geotrellis.server.ogc.wcs.WcsModel
import geotrellis.server.ogc.wms.{WmsModel, WmsParentLayerMeta}
import geotrellis.server.ogc.wms.wmsScope
import _root_.io.circe.syntax._
import opengis.wms.{Name, OnlineResource, Service}

import java.util.UUID

class OgcImplicits[P: ProjectStore](layers: P, xa: Transactor[IO])
    extends ToProjectStoreOps {

  private def compositesToOgcStyles(
      composites: Map[String, ColorComposite]): List[OgcStyle] =
    composites.values map { OgcStyles.fromColorComposite _ } toList

  private def getStyles(projectLayerId: UUID): IO[List[OgcStyle]] =
    for {
      configured <- ProjectLayerDatasourcesDao
        .listProjectLayerDatasources(projectLayerId)
        .transact(xa) map { datasources =>
        (datasources flatMap { datasource =>
          compositesToOgcStyles(datasource.composites)
        })
      }
      rf <- ProjectLayerDao
        .unsafeGetProjectLayerById(projectLayerId)
        .transact(xa) map { projectLayer =>
        (projectLayer.singleBandOptions asJson)
          .as[SingleBandOptions.Params] map {
          OgcStyles.fromSingleBandOptions(_, "Single Band", indexBand = false)
        } toList
      }
    } yield { configured ++ rf }

  private def projectLayerToSimpleSource(
      projectLayer: ProjectLayer): IO[(SimpleSource, List[CRS])] =
    for {
      rsm <- BacksplashMosaic.toRasterSource(
        layers.read(projectLayer.id, None, None, None)
      )
      crs <- BacksplashMosaic.getRasterSourceOriginalCRS(
        layers.read(projectLayer.id, None, None, None)
      )
      ogcStyles <- getStyles(projectLayer.id)
    } yield {
      (
        SimpleSource(
          projectLayer.name,
          projectLayer.id.toString,
          rsm,
          ogcStyles
        ),
        crs
      )

    }

  private def getSources(projectId: UUID): IO[List[(OgcSource, List[CRS])]] =
    for {
      projectLayers <- ProjectLayerDao
        .listProjectLayersWithImagery(projectId)
        .transact(xa)
      sourcesAndCRS <- projectLayers traverse { projectLayerToSimpleSource _ }
    } yield sourcesAndCRS

  implicit val projectOgcStore: OgcStore[ProjectDao] =
    new OgcStore[ProjectDao] {
      def getWcsModel(self: ProjectDao, id: UUID): IO[WcsModel] =
        for {
          sourcesAndCRS <- getSources(id)
          sources = sourcesAndCRS.map(_._1)
        } yield {
          val serviceMeta = ServiceMetadata(
            Identification("", "", Nil, Nil, None, Nil),
            Provider("", None, None)
          )
          WcsModel(serviceMeta, sources)
        }

      def getWmsModel(self: ProjectDao, id: UUID): IO[WmsModel] =
        for {
          sourcesAndCRS <- getSources(id)
          sources = sourcesAndCRS.map(_._1)
          crs = sourcesAndCRS.flatMap(_._2)
          service <- getWmsServiceMetadata(self, id)
        } yield {
          val parentLayerMeta = WmsParentLayerMeta(
            None,
            "Raster Foundry WMS Layer",
            None,
            List(LatLng, WebMercator) ++ crs)
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

      def getLayerHistogram(self: ProjectDao,
                            id: UUID): IO[List[Histogram[Double]]] =
        LayerAttributeDao().getProjectLayerHistogram(id, xa) map { histArrays =>
          histArrays map { _.toList } reduce {
            (hists1: List[Histogram[Double]],
             hists2: List[Histogram[Double]]) =>
              hists1 zip hists2 map {
                case (h1, h2) => h1 merge h2
              }
          }
        }
    }
}
