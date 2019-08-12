package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.{BacksplashMosaic, OgcStore, ProjectStore}
import com.rasterfoundry.backsplash.color.OgcStyles
import com.rasterfoundry.backsplash.{
  OgcStore,
  ProjectStore,
  RasterSourceWithMetadata
}
import com.rasterfoundry.backsplash.color.{OgcStyles, SingleBandOptions}
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.datamodel.{
  ColorComposite,
  ProjectLayer,
  SingleBandOptions
}
import com.rasterfoundry.database.{
  LayerAttributeDao,
  ProjectDao,
  ProjectLayerDao,
  ProjectLayerDatasourcesDao,
  RasterSourceMetadataDao
}
import cats.effect.{ContextShift, IO}
import cats.implicits._
import doobie.Transactor
import doobie.implicits._
import geotrellis.contrib.vlm.MosaicRasterSource
import geotrellis.proj4.{CRS, LatLng, WebMercator}
import geotrellis.raster.histogram.Histogram
import geotrellis.server.ogc.{OgcSource, OgcStyle, SimpleSource}
import geotrellis.server.ogc.ows._
import geotrellis.server.ogc.wcs.WcsModel
import geotrellis.server.ogc.wms.{WmsModel, WmsParentLayerMeta}
import geotrellis.server.ogc.wms.wmsScope
import _root_.io.circe.syntax._
import opengis.wms.{Name, OnlineResource, Service}
import java.util.UUID

import com.rasterfoundry.backsplash.error.NoScenesException
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.raster.{CellSize, GridExtent}

import scala.collection.parallel.ForkJoinTaskSupport

class OgcImplicits[P: ProjectStore](layers: P, xa: Transactor[IO])(
    implicit contextShift: ContextShift[IO]
) extends ToProjectStoreOps {

  implicit val transactor: Transactor[IO] = xa

  private def compositesToOgcStyles(
      composites: Map[String, ColorComposite]
  ): List[OgcStyle] =
    composites.values map {
      OgcStyles.fromColorComposite _
    } toList

  private def getStyles(projectLayerId: UUID): IO[List[OgcStyle]] = {
    println(s"Getting Styles")
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
    } yield {
      configured ++ rf
    }
  }

  private def projectLayerToSimpleSource(
      projectLayer: ProjectLayer
  ): IO[(SimpleSource, List[CRS])] = {
    println(s"Converting Project Layer ${projectLayer.id} to Simple Source")
    val layer = layers.read(projectLayer.id, None, None, None).compile.toList
    val rasterSourcesIO = layer.flatMap { backsplashImages =>
      backsplashImages.map { image =>
        RasterSourceMetadataDao.select(image.imageId).transact(xa).map { rsm =>
          val rasterSource = GDALRasterSource(rsm.dataPath)
          RasterSourceWithMetadata(rsm, rasterSource)
        }
      }.sequence
    }
    for {
      rasterSources <- rasterSourcesIO
      styles <- getStyles(projectLayer.id)
    } yield {
      println(s"Got Raster Sources and Styles")
      val crses = rasterSources.map(_.crs).distinct
      println(s"Got CRSES")
      val mosaicRasterSource = rasterSources.toNel match {
        case Some(rasterSources) => {

          val reprojectedRasterSources =
            rasterSources.map(_.reproject(WebMercator))
          val reprojectedExtents = reprojectedRasterSources.map(_.gridExtent)
          val heights = reprojectedExtents map (_.cellheight)
          val avgHeight = heights.toList.sum / heights.length
          val cs: CellSize = CellSize(avgHeight, avgHeight)

          val sourceCombinedGrid = reprojectedRasterSources
            .map(_.gridExtent)
            .reduce((a: GridExtent[Long], b: GridExtent[Long]) => {
              val combined = a.extent.combine(b.extent)
              GridExtent[Long](combined, cs)
            })

          MosaicRasterSource(reprojectedRasterSources,
                             WebMercator,
                             sourceCombinedGrid)
        }
        case _ => throw new Exception("No Rastersources")
      }
      println(s"Got MosaicRasterSource")

      (
        SimpleSource(
          projectLayer.name,
          projectLayer.id.toString,
          mosaicRasterSource,
          styles
        ),
        crses
      )
    }
  }

  private def getSources(projectId: UUID): IO[List[(OgcSource, List[CRS])]] =
    for {
      projectLayers <- ProjectLayerDao
        .listProjectLayersWithImagery(projectId)
        .transact(xa)
      sourcesAndCRS <- {
        val distinctProjectLayers = projectLayers.groupBy(_.id).map(_._2.head)
        distinctProjectLayers.toList parTraverse {
          projectLayerToSimpleSource _
        }
      }
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
          _ <- IO(println(s"Getting Sources"))
          sourcesAndCRS <- getSources(id)
          sources = sourcesAndCRS.map(_._1)
          crs = sourcesAndCRS.flatMap(_._2)
          service <- getWmsServiceMetadata(self, id)
        } yield {
          val parentLayerMeta = WmsParentLayerMeta(
            None,
            "Raster Foundry WMS Layer",
            None,
            List(LatLng, WebMercator) ++ crs
          )
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

      def getLayerHistogram(
          self: ProjectDao,
          id: UUID
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
