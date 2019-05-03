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
import com.rasterfoundry.datamodel.ProjectLayer
import com.typesafe.scalalogging.LazyLogging

object OverviewGenerator extends LazyLogging {

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

    logger.debug(s"Using ${rasterSources.length} rastersources")

    rasterSources.toNel match {
      case Some(rasterSourcesList) =>
        logger.debug(s"Generating mosaic raster source")
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
      case _ => None
    }
  }

  def writeOverviewToS3(tiff: MultibandGeoTiff, uri: String): Unit = {
    logger.debug(s"Writing tiff to $uri")
    val s3Uri = new AmazonS3URI(uri)

    S3Client.DEFAULT.putObject(s3Uri.getBucket,
                               s3Uri.getKey,
                               tiff.toCloudOptimizedByteArray)
    ()
  }

  def createOverview(overviewInput: OverviewInput): Option[ProjectLayer] = {

    logger.debug(s"Retrieving JWT with Refresh Token")
    val authToken = HttpClient.getSystemToken(overviewInput.refreshToken)
    logger.debug(s"Getting project layer scenes")
    val initialProjectScenes = HttpClient.getProjectLayerScenes(
      authToken,
      overviewInput.projectId,
      overviewInput.projectLayerId
    )

    logger.debug(s"Creating project overview")
    val projectOverviewOption =
      OverviewGenerator.createProjectOverview(initialProjectScenes,
                                              overviewInput.pixelSizeMeters)

    logger.debug(
      "Checking if scenes have been updated or removed from project layer")
    val currentProjectScenes = HttpClient.getProjectLayerScenes(
      authToken,
      overviewInput.projectId,
      overviewInput.projectLayerId
    )
    (currentProjectScenes == initialProjectScenes, projectOverviewOption) match {
      case (true, Some(projectOverview)) =>
        writeOverviewToS3(projectOverview, overviewInput.outputLocation)
        logger.debug("Updating project layer in API with overview")
        val projectLayer = HttpClient.updateProjectWithOverview(
          authToken,
          overviewInput.projectId,
          overviewInput.projectLayerId,
          overviewInput.outputLocation)
        Some(projectLayer)
      case _ =>
        logger.debug(
          "Skipping adding project overview, project layer scenes have changed since overview generated")
        None
    }

  }
}
