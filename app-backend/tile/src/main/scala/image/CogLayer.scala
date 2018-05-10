package com.azavea.rf.tile.image

import cats._
import cats.implicits._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.histogram._
import geotrellis.raster.resample._
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
  def tileLatLng(z: Int, x: Int, y: Int): Point = {
    // Reference https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
    val coordinateX = x.toDouble / (1 << z) * 360.0 - 180.0
    val coordinateY = math.toDegrees(math.atan(math.sinh(math.Pi * (1.0 - 2.0 * y.toDouble / (1 << z)))))
    Point(coordinateX, coordinateY)
  }

  def tileExtent(z: Int, x: Int, y: Int, crs: CRS): Extent = {
    val nw = tileLatLng(z, x, y)
    val se = tileLatLng(z, x + 1, y + 1)
    val extent = Extent(nw.x, se.y, se.x, nw.y)
    crs match {
      case LatLng => extent
      case _ => extent.reproject(LatLng, crs)
    }
  }

  def cellSizeForZoom(tiff: MultibandGeoTiff, zoom: Int): CellSize = {
    val layoutScheme = ZoomedLayoutScheme(tiff.crs, 256)
    val naturalLevel = layoutScheme.levelFor(tiff.extent, tiff.cellSize)
    zoom match {
      case z if z >= naturalLevel.zoom => tiff.cellSize
      case z if z > 0 => new CellSize(
        tiff.cellSize.width * (scala.math.pow(2, naturalLevel.zoom - z)),
        tiff.cellSize.height * (scala.math.pow(2, (naturalLevel.zoom - z))))
      case _ => CellSize(0, 0)
    }
  }

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
      extent = tileExtent(z, x, y, tiff.crs)
      cellSize = cellSizeForZoom(tiff, z)
      cropped <- Try(tiff.crop(extent, cellSize, ResampleMethod.DEFAULT, AutoHigherResolution)).toOption
      raster = cropped.reproject(tiff.crs, WebMercator)
    } yield raster.tile
  }

}

