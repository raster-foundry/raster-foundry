package com.rasterfoundry

import cats.effect._
import com.amazonaws.services.s3.AmazonS3URI
import com.colisweb.tracing.core.TracingContext
import geotrellis.store.s3.util.S3RangeReader
import geotrellis.util.{FileRangeReader, HttpRangeReader, StreamingByteReader}
import io.circe.KeyEncoder
import org.apache.http.client.utils.URLEncodedUtils
import org.http4s.Request
import org.http4s.util.CaseInsensitiveString
import software.amazon.awssdk.services.s3.S3Client

import java.net.{URI, URL}
import java.nio.charset.Charset
import java.nio.file.Paths

package object backsplash {

  type BacksplashMosaic = IO[(TracingContext[IO], List[BacksplashImage[IO]])]

  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    def apply(key: Double): String = key.toString
  }

  /** Helper implicit class to make it make pulling out a trace ID
    * consistent across the code base, if missing an empty string is
    * returned
    *
    * @param req http4s request object
    * @tparam F effect type
    */
  implicit class requestWithTraceID[F[_]](req: Request[F]) {
    def traceID: String = {
      req.headers.get(CaseInsensitiveString("X-Amzn-Trace-Id")) match {
        case Some(s) => s.toString
        case _       => ""
      }
    }
  }

  /** AWS' S3 client has an internal connection pool, in order to maximize throughput
    * we try to reuse it and only instantiate one
    *
    */
  lazy val s3Client = S3Client.builder().build()

  /** Replicates byte reader functionality in GeoTrellis that we don't get
    * access to
    *
    * @param uri
    * @return
    */
  def getByteReader(uri: String): StreamingByteReader = {

    val javaURI = new URI(uri)
    val noQueryParams =
      URLEncodedUtils.parse(uri, Charset.forName("UTF-8")).isEmpty

    val rr = javaURI.getScheme match {
      case null =>
        FileRangeReader(Paths.get(uri).toFile)

      case "file" =>
        FileRangeReader(Paths.get(javaURI).toFile)

      case "http" | "https" if noQueryParams =>
        HttpRangeReader(new URL(uri))

      case "http" | "https" =>
        new HttpRangeReader(new URL(uri), false)

      case "s3" =>
        val s3Uri = new AmazonS3URI(java.net.URLDecoder.decode(uri, "UTF-8"))
        S3RangeReader(s3Uri.getBucket, s3Uri.getKey, s3Client)

      case scheme =>
        throw new IllegalArgumentException(
          s"Unable to read scheme $scheme at $uri")
    }
    new StreamingByteReader(rr, 128000)
  }
}
