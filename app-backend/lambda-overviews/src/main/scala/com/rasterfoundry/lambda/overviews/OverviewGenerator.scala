package com.rasterfoundry.lambda.overviews

import com.amazonaws.services.s3.AmazonS3URI
import geotrellis.contrib.vlm.MosaicRasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.proj4.WebMercator
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.raster.{CellSize, GridExtent}
import geotrellis.spark.io.s3.S3Client
import cats.syntax.list._

object OverviewGenerator {

  def createProjectOverview(sceneURIs: List[String],
                            pixelSize: Int): Option[MultibandGeoTiff] = {

    val rasterSources = sceneURIs.map {
      GeoTiffRasterSource(_).reproject(WebMercator)
    }

    val reprojectedExtents = rasterSources.map(_.gridExtent)
    val heights = reprojectedExtents map (_.cellheight)
    val avgHeight = heights.sum / heights.length
    val cs: CellSize = CellSize(avgHeight, avgHeight)

    val sourceCombinedGrid = rasterSources
      .map(_.gridExtent)
      .reduce((a: GridExtent[Long], b: GridExtent[Long]) => {
        val combined = a.extent.combine(b.extent)
        GridExtent[Long](combined, cs)
      })

    rasterSources.toNel match {
      case Some(rasterSourcesList) => {
        val mosaicRasterSource =
          MosaicRasterSource(rasterSourcesList, WebMercator, sourceCombinedGrid)

        val gridExtent =
          GridExtent[Long](mosaicRasterSource.extent,
                           CellSize(pixelSize, pixelSize))

        val rasterOption = mosaicRasterSource
          .resampleToGrid(gridExtent)
          .read
        rasterOption match {
          case Some(raster) =>
            Some(
              MultibandGeoTiff(raster, WebMercator).withOverviews(
                NearestNeighbor))
          case _ => None
        }
      }
      case _ => None
    }
  }

  def writeOverviewToS3(tiff: MultibandGeoTiff, uri: String): Unit = {
    val s3Uri = new AmazonS3URI(uri)

    S3Client.DEFAULT.putObject(s3Uri.getBucket,
                               s3Uri.getKey,
                               tiff.toCloudOptimizedByteArray)
    ()
  }

  def createOverview(overviewInput: OverviewInput): Unit = {

    val authToken = HttpClient.getSystemToken(overviewInput.refreshToken)
    val initialProjectScenes = HttpClient.getProjectLayerScenes(
      authToken,
      overviewInput.projectId,
      overviewInput.projectLayerId
    )
    val projectOverviewOption =
      OverviewGenerator.createProjectOverview(initialProjectScenes,
                                              overviewInput.pixelSizeMeters)
    val currentProjectScenes = HttpClient.getProjectLayerScenes(
      authToken,
      overviewInput.projectId,
      overviewInput.projectLayerId
    )
    (currentProjectScenes == initialProjectScenes, projectOverviewOption) match {
      case (true, Some(projectOverview)) =>
        writeOverviewToS3(projectOverview, overviewInput.outputLocation)
      case _ => ()
    }
  }
}
