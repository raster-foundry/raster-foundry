package com.rasterfoundry.common.utils

import com.rasterfoundry.common.S3

import com.typesafe.scalalogging.LazyLogging
import geotrellis.spark.io.http.util.HttpRangeReader
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.spark.io.s3.util.S3RangeReader
import geotrellis.util.{FileRangeReader, RangeReader}
import org.apache.http.client.utils.URLEncodedUtils

import java.net.{URI, URL}
import java.nio.charset.Charset
import java.nio.file.Paths

object RangeReaderUtils extends LazyLogging {
  def fromUri(uri: String): Option[RangeReader] = {
    val javaUri = new URI(uri)

    /**
      * Links can be signed for instance via HMAC-SHA,
      * it means that request signature can be specific at least to the METHOD
      * (GET and HEAD requests would have different auth signature)
      *
      * AWS S3 would return 403 as each METHOD has a different signature,
      * see: https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
      *
      * In all cases there are some query params force GET method usage
      */
    val noQueryParams =
      URLEncodedUtils.parse(uri, Charset.forName("UTF-8")).isEmpty

    javaUri.getScheme match {
      case "file" | null =>
        Some(FileRangeReader(Paths.get(javaUri).toFile))

      case "http" | "https" if noQueryParams =>
        Some(HttpRangeReader(new URL(uri)))

      case "http" | "https" =>
        Some(new HttpRangeReader(new URL(uri), false))

      case "s3" =>
        val s3Uri = S3.createS3Uri(java.net.URLDecoder.decode(uri, "UTF-8"))
        val s3Client = new AmazonS3Client(S3().client)
        Some(S3RangeReader(s3Uri.getBucket, s3Uri.getKey, s3Client))

      case _ =>
        None
    }
  }

}
