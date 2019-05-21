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
import com.typesafe.scalalogging.LazyLogging

import com.rasterfoundry.datamodel.OverviewInput

object OverviewGenerator extends LazyLogging {

  def createProjectOverview(sceneURIs: List[String],
                            pixelSize: Double): Option[MultibandGeoTiff] = {

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

    println(s"Using ${rasterSources.length} rastersources")

    rasterSources.toNel match {
      case Some(rasterSourcesList) =>
        println("Generating mosaic raster source")
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
    println(s"Writing tiff to $uri")
    val s3Uri = new AmazonS3URI(uri)

    S3Client.DEFAULT.putObject(s3Uri.getBucket,
                               s3Uri.getKey,
                               tiff.toCloudOptimizedByteArray)
    ()
  }

  def createOverview(overviewInput: OverviewInput): Option[Int] = {

    println("Retrieving JWT with Refresh Token")
    val authToken = HttpClient.getSystemToken(overviewInput.refreshToken)
    println("Getting project layer scenes")
    val initialProjectScenes = HttpClient.getProjectLayerScenes(
      authToken,
      overviewInput.projectId,
      overviewInput.projectLayerId
    )

    println("Creating project overview")
    val projectOverviewOption =
      OverviewGenerator.createProjectOverview(
        initialProjectScenes,
        156412 / math.pow(2, overviewInput.minZoomLevel))

    println(
      "Checking if scenes have been updated or removed from project layer")
    val currentProjectScenes = HttpClient.getProjectLayerScenes(
      authToken,
      overviewInput.projectId,
      overviewInput.projectLayerId
    )
    (currentProjectScenes == initialProjectScenes, projectOverviewOption) match {
      case (true, Some(projectOverview)) =>
        writeOverviewToS3(projectOverview, overviewInput.outputLocation)
        println("Updating project layer in API with overview")
        val layerUpdateStatus =
          HttpClient.updateProjectWithOverview(authToken, overviewInput)
        Some(layerUpdateStatus)
      case _ =>
        println(
          "Skipping adding project overview, project layer scenes have changed since overview generated")
        None
    }
  }
}
