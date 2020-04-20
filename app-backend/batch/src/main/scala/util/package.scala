package com.rasterfoundry.batch

import com.rasterfoundry.common.S3

import com.amazonaws.services.s3.AmazonS3URI
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.parser.parse
import org.apache.commons.io.IOUtils
import org.apache.hadoop.conf.Configuration

import java.io._
import java.net._
import java.nio.charset.Charset

package object util extends LazyLogging {

  val s3Client = S3()

  implicit class ConfigurationMethods(conf: Configuration) {
    @SuppressWarnings(Array("NullParameter"))
    def isKeyUnset(key: String): Boolean = conf.get(key) == null
  }

  implicit class InputStreamMethods(is: InputStream) {
    def toJson: Option[Json] = {
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
      val s3uri = new AmazonS3URI(uri)
      s3Client.getObject(s3uri.getBucket, s3uri.getKey).getObjectContent
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
      IOUtils.toString(is, Charset.defaultCharset())
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
