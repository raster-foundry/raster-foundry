package com.azavea.rf.batch

import io.circe.parser.parse
import cats.implicits._

import geotrellis.raster.io.geotiff.reader.TiffTagsReader
import geotrellis.raster.io.geotiff.tags.TiffTags
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.spark.io.s3.util.S3RangeReader

import com.amazonaws.auth._
import com.amazonaws.services.s3.{AmazonS3URI, AmazonS3Client => AWSAmazonS3Client}
import org.apache.commons.io.IOUtils
import org.apache.hadoop.conf.Configuration

import java.io._
import java.net._

package object util {
  implicit class ConfigurationMethods(conf: Configuration) {
    def isKeyUnset(key: String): Boolean = conf.get(key) == null
  }

  implicit class InputStreamMethods(is: InputStream) {
    def toJson = {
      val lines = scala.io.Source.fromInputStream(is).getLines
      val json = lines.mkString(" ")
      is.close()
      parse(json).toOption
    }
  }

  implicit class ThrowableMethods[T <: Throwable](e: T) {
    def stackTraceString: String = {
      val sw = new StringWriter
      e.printStackTrace(new PrintWriter(sw))
      sw.toString
    }
  }

  def getTiffTags(uri: URI): TiffTags = uri.getScheme match {
    case "file" =>
      TiffTagsReader.read(uri.toString)
    case "s3" | "https" | "http" =>
      val s3Uri = new AmazonS3URI(uri)
      val s3Client = new AmazonS3Client(new AWSAmazonS3Client(new DefaultAWSCredentialsProviderChain))
      val s3RangeReader = S3RangeReader(s3Uri.getBucket, s3Uri.getKey, s3Client)
      TiffTagsReader.read(s3RangeReader)
    case _ =>
      throw new IllegalArgumentException(s"Resource at $uri is not valid")
  }

  /** Convert URIs into input streams, branching based on URI type */
  def getStream(uri: URI): InputStream = uri.getScheme match {
    case "file" =>
      new FileInputStream(new File(uri))
    case "http" =>
      uri.toURL.openStream
    case "https" =>
      uri.toURL.openStream
    case "s3" =>
      val client = new AWSAmazonS3Client(new DefaultAWSCredentialsProviderChain)
      val s3uri = new AmazonS3URI(uri)
      client.getObject(s3uri.getBucket, s3uri.getKey).getObjectContent
    case _ =>
      throw new IllegalArgumentException(s"Resource at $uri is not valid")
  }

  /** Use a provided URI to get an array of bytes */
  def readBytes(fileUri: URI): Array[Byte] = {
    val is = getStream(fileUri)
    try {
      IOUtils.toByteArray(is)
    } finally {
      is.close()
    }
  }

  /** Use a provided URI to get a string */
  def readString(fileUri: URI): String = {
    val is = getStream(fileUri)
    try {
      IOUtils.toString(is)
    } finally {
      is.close()
    }
  }

  def isUriExists(uri: String): Boolean =
    isUriExists(new URI(uri))

  def isUriExists(uri: URI): Boolean = {
    try {
      getStream(uri)
      true
    } catch {
      case _: FileNotFoundException => false
    }
  }
}
