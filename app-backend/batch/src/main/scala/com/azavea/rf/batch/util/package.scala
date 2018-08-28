package com.azavea.rf.batch

import java.io._
import java.net._
import java.util.Scanner

import cats.implicits._
import com.amazonaws.auth._
import com.amazonaws.services.s3.{
  AmazonS3URI,
  AmazonS3Client => AWSAmazonS3Client
}
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.io.geotiff.reader.TiffTagsReader
import geotrellis.raster.io.geotiff.tags.TiffTags
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.spark.io.s3.util.S3RangeReader
import io.circe.parser.parse
import org.apache.commons.io.IOUtils
import org.apache.hadoop.conf.Configuration

package object util extends LazyLogging {

  implicit class ConfigurationMethods(conf: Configuration) {
    @SuppressWarnings(Array("NullParameter"))
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
      val s3Uri = new AmazonS3URI(
        java.net.URLDecoder.decode(uri.toString, "UTF-8"))
      val s3Client = new AmazonS3Client(
        new AWSAmazonS3Client(new DefaultAWSCredentialsProviderChain))
      val s3RangeReader = S3RangeReader(s3Uri.getBucket, s3Uri.getKey, s3Client)
      TiffTagsReader.read(s3RangeReader)
    case _ =>
      throw new IllegalArgumentException(s"Resource at $uri is not valid")
  }

  /** Convert URIs into input streams, branching based on URI type */
  def getStream(uri: URI): InputStream = uri.getScheme match {
    case "file" =>
      uri.getAuthority match {
        case relRoot: String =>
          val absolute = new File(new File("./"), relRoot + uri.getPath)
          logger.debug(s"relative route detected: ${absolute}")
          new FileInputStream(absolute)
        case _ =>
          new FileInputStream(new File(uri))
      }
    case "http" | "https" =>
      uri.toURL.openStream
    case "s3" =>
      val client = new AWSAmazonS3Client(new DefaultAWSCredentialsProviderChain)
      val s3uri = new AmazonS3URI(uri)
      client.getObject(s3uri.getBucket, s3uri.getKey).getObjectContent
    case _ =>
      throw new IllegalArgumentException(s"Resource at $uri is not valid")
  }

  def combineUris(targetName: URI, prefix: URI): URI = {
    targetName.getScheme match {
      case "file" | "http" | "https" | "s3" => targetName
      case _ =>
        if (prefix.toString.endsWith("/"))
          new URI(prefix.toString + targetName.toString)
        else new URI(prefix.toString + "/" + targetName.toString)
    }
  }

  /** Converts URI's into input streams, branching on URI type. Handles relative URIs given a root URI */
  def getStream(uri: URI, rootUri: URI): InputStream = {
    getStream(combineUris(uri, rootUri).normalize)
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
      // A jdk [input stream -> string] method which avoids the IOUtils' 'toString' deprecation:
      // https://community.oracle.com/blogs/pat/2004/10/23/stupid-scanner-tricks
      val scan: Scanner = new Scanner(is).useDelimiter("\\A")
      if (scan.hasNext()) scan.next() else ""
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
