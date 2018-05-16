package com.azavea.rf.common.utils

import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, Point, Polygon}
import geotrellis.util.{ FileRangeReader, RangeReader }
import geotrellis.spark.io.s3.util.S3RangeReader
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.spark.io.http.util.HttpRangeReader

import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.{AmazonS3URI, AmazonS3Client => AWSAmazonS3Client}
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain

import scala.util.Try
import java.net.URI
import java.net.URL


object RangeReaderUtils extends LazyLogging {
  def fromUri(uri: String): Option[RangeReader] = {
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

}