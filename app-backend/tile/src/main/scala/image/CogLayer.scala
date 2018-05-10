package com.azavea.rf.tile.image

import cats._
import cats.implicits._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.histogram._
import geotrellis.raster.resample._
import geotrellis.raster.reproject._
import geotrellis.raster.render._
import geotrellis.raster.render.ColorRamps.Viridis
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.proj4._
import geotrellis.util.{ FileRangeReader, RangeReader }
import geotrellis.raster.io.geotiff.writer.GeoTiffWriter
import geotrellis.spark.tiling._
import geotrellis.spark.io.s3.util.S3RangeReader
import geotrellis.spark.io.http.util.HttpRangeReader
import com.amazonaws.services.s3.AmazonS3URI
import geotrellis.spark.io.s3.AmazonS3Client
import com.amazonaws.services.s3.{AmazonS3URI, AmazonS3Client => AWSAmazonS3Client}
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import scala.util.Properties
import scala.math
import scala.util.Try
import java.net.URI
import java.net.URL


object CogLayer {
  private val TmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray
  
  def getRangeReader(uri: String): Option[RangeReader] = {
    import java.nio.file._

    val javaUri = new URI(uri)
    javaUri.getScheme match {
      case "file" | null =>
        Some(FileRangeReader(Paths.get(javaUri).toFile))

      case "http" | "https" =>
        Some(HttpRangeReader(new URL(uri)))

      case "s3" =>
        val s3Uri = new AmazonS3URI(java.net.URLDecoder.decode(uri, "UTF-8"))
        val s3Client = new AmazonS3Client(new AWSAmazonS3Client(new DefaultAWSCredentialsProviderChain))
        Some(S3RangeReader(s3Uri.getBucket, s3Uri.getKey, s3Client))

      case scheme =>
        None
    }
  }

  def fetch(uri: String, z: Int, x: Int, y: Int): Option[MultibandTile] = {
    for {
      rr <- getRangeReader(uri)
      tiff = GeoTiffReader.readMultiband(rr, decompress = false, streaming = true)
      transform = Proj4Transform(tiff.crs, WebMercator)
      inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      tmsTileExtent = TmsLevels(z).mapTransform.keyToExtent(x, y)
      tmsTileRE = RasterExtent(tmsTileExtent, TmsLevels(z).cellSize)
      tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)
      raster <- Try(tiff.crop(tiffTileRE.extent, tiffTileRE.cellSize, ResampleMethod.DEFAULT, AutoHigherResolution)).toOption      
    } yield raster.reproject(tmsTileRE, transform, inverseTransform).tile
  }

}

