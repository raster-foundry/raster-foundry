package com.azavea.rf.ingest

import geotrellis.raster.io.geotiff.tags.TiffTags
import geotrellis.raster.io.geotiff.reader.TiffTagsReader
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.spark.io.s3.util.S3RangeReader

import com.amazonaws.services.s3.{ AmazonS3URI, AmazonS3Client => AWSAmazonS3Client }
import com.amazonaws.services.s3.model.{ListObjectsRequest, ObjectListing}
import com.amazonaws.auth._
import org.apache.commons.io.IOUtils
import org.apache.spark.rdd.RDD
import org.apache.spark._

import java.io._
import java.net._
import java.util._

package object util {

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
      client.getObject(s3uri.getBucket, s3uri.getKey).getObjectContent()
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
}
