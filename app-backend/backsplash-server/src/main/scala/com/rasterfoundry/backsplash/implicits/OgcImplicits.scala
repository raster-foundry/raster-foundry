package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.RenderableStore.ToRenderableStoreOps
import com.rasterfoundry.backsplash.color.OgcStyles
import com.rasterfoundry.backsplash.{
  BacksplashMosaic,
  OgcStore,
  RenderableStore
}
import com.rasterfoundry.database.{
  LayerAttributeDao,
  ProjectDao,
  ProjectLayerDao,
  ProjectLayerDatasourcesDao
}
import com.rasterfoundry.datamodel.{
  ColorComposite,
  ProjectLayer,
  SingleBandOptions
}

import _root_.io.circe.syntax._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.colisweb.tracing.TracingContext
import doobie.Transactor
import doobie.implicits._
import geotrellis.raster.{Histogram, MosaicRasterSource}
import geotrellis.proj4.{CRS, LatLng, WebMercator}
import geotrellis.server.ogc.ows._
import geotrellis.server.ogc.wcs.WcsModel
import geotrellis.server.ogc.wms.wmsScope
import geotrellis.server.ogc.wms.{WmsModel, WmsParentLayerMeta}
import geotrellis.server.ogc.{OgcSource, OgcSourceRepository, SimpleSource}
import geotrellis.server.ogc.style.OgcStyle
import opengis.wms.{Name, OnlineResource, Service}

import java.util.UUID

class OgcImplicits[R: RenderableStore](layers: R, xa: Transactor[IO])(
    implicit contextShift: ContextShift[IO]
) extends ToRenderableStoreOps {

  private def compositesToOgcStyles(
      composites: Map[String, ColorComposite]
  ): List[OgcStyle] =
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
      projectLayer: ProjectLayer,
      tracingContext: TracingContext[IO]
  ): IO[(SimpleSource, List[CRS])] =
    (
      BacksplashMosaic.toRasterSource(
        layers.read(projectLayer.id, None, None, None, tracingContext)
      ),
      BacksplashMosaic.getRasterSourceOriginalCRS(
        layers.read(projectLayer.id, None, None, None, tracingContext)
      ),
      getStyles(projectLayer.id)
    ).parMapN(
      (rsm: MosaicRasterSource, crs: List[CRS], ogcStyles: List[OgcStyle]) => {
        (
          SimpleSource(
            projectLayer.name,
            projectLayer.id.toString,
            rsm,
            ogcStyles.headOption map { _.name },
            ogcStyles
          ),
          crs
        )
      }
    )

  private def getSources(
      projectId: UUID,
      tracingContext: TracingContext[IO]
  ): IO[List[(OgcSource, List[CRS])]] =
    for {
      projectLayers <- ProjectLayerDao
        .listProjectLayersWithImagery(projectId)
        .transact(xa)
      sourcesAndCRS <- projectLayers parTraverse { s =>
        projectLayerToSimpleSource(s, tracingContext)
      }
    } yield sourcesAndCRS

  implicit val projectOgcStore: OgcStore[ProjectDao] =
    new OgcStore[ProjectDao] {
      def getWcsModel(
          self: ProjectDao,
          id: UUID,
          tracingContext: TracingContext[IO]
      ): IO[WcsModel] =
        for {
          sourcesAndCRS <- getSources(id, tracingContext)
          sources = sourcesAndCRS.map(_._1)
        } yield {
          val serviceMeta = ServiceMetadata(
            Identification("", "", Nil, Nil, None, Nil),
            Provider("", None, None)
          )
          WcsModel(serviceMeta, new OgcSourceRepository(sources))
        }

      def getWmsModel(
          self: ProjectDao,
          id: UUID,
          tracingContext: TracingContext[IO]
      ): IO[WmsModel] =
        for {
          sourcesAndCRS <- getSources(id, tracingContext)
          sources = sourcesAndCRS.map(_._1)
          crs = sourcesAndCRS.flatMap(_._2)
          service <- getWmsServiceMetadata(self, id, tracingContext)
        } yield {
          val parentLayerMeta = WmsParentLayerMeta(
            None,
            "Raster Foundry WMS Layer",
            None,
            List(LatLng, WebMercator) ++ crs
          )
          WmsModel(service, parentLayerMeta, new OgcSourceRepository(sources))
        }

      def getWmsServiceMetadata(
          self: ProjectDao,
          id: UUID,
          tracingContext: TracingContext[IO]
      ): IO[Service] =
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

      def getLayerHistogram(
          self: ProjectDao,
          id: UUID,
          tracingContext: TracingContext[IO]
      ): IO[List[Histogram[Double]]] =
        LayerAttributeDao().getProjectLayerHistogram(id, xa) map { histArrays =>
          histArrays map { _.toList } reduce {
            (
                hists1: List[Histogram[Double]],
                hists2: List[Histogram[Double]]
            ) =>
              hists1 zip hists2 map {
                case (h1, h2) => h1 merge h2
              }
          }
        }
    }
}
