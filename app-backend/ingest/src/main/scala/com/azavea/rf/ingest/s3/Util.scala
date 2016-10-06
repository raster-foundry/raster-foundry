package com.azavea.rf.ingest.s3

import com.amazonaws.auth._
import com.amazonaws.services.s3.model.{ListObjectsRequest, ObjectListing}
import com.amazonaws.services.s3.{AmazonS3Client, AmazonS3URI}
import java.io._
import java.net._
import java.util.HashMap
import java.util._
import org.apache.commons.io.IOUtils
import org.apache.spark._
import org.apache.spark.rdd.RDD
import scala.collection.JavaConverters._
import scala.collection.mutable

import scala.util.matching.Regex

object Util {
  val cred = new DefaultAWSCredentialsProviderChain()
  val client = new AmazonS3Client(cred)

  // From GT codebase
  private val idRx = "[A-Z0-9]{20}"
  private val keyRx = "[a-zA-Z0-9+/]+={0,2}"
  private val slug = "[a-zA-Z0-9-]+"
  val S3UrlRx = new Regex(s"""s3[an]?://(?:($idRx):($keyRx)@)?($slug)/{0,1}(.*)""", "aws_id", "aws_key", "bucket", "prefix")

  def listKeysHttp(url: String, ext: String, recursive: Boolean): Array[String] = {
    val S3UrlRx(_, _, bucket, prefix) = url
    listKeysHttp(bucket, prefix, ext, recursive)
  }

  def listKeysHttp(s3bucket: String, s3prefix: String, ext: String, recursive: Boolean = false): Array[String] = {
    val objectRequest = (new ListObjectsRequest)
      .withBucketName(s3bucket)
      .withPrefix(s3prefix)

    if (! recursive) { // Avoid digging into a deeper directory
      objectRequest.withDelimiter("/")
    }

    listKeys(objectRequest)
      .collect { case key if key.endsWith(ext) =>
        s"https://s3.amazonaws.com/${s3bucket}/${key}"
      }.toArray
  }

  def listKeysS3(url: String, ext: String, recursive: Boolean): Array[String] = {
    val S3UrlRx(_, _, bucket, prefix) = url
    listKeysS3(bucket, prefix, ext, recursive)
  }

  def listKeysS3(s3bucket: String, s3prefix: String, ext: String, recursive: Boolean = false): Array[String] = {
    val objectRequest = (new ListObjectsRequest)
      .withBucketName(s3bucket)
      .withPrefix(s3prefix)

    if (! recursive) { // Avoid digging into a deeper directory
      objectRequest.withDelimiter("/")
    }

    listKeys(objectRequest)
      .collect { case key if key.endsWith(ext) => s"s3://${s3bucket}/${key}" }.toArray
  }

  // Copied from GeoTrellis codebase
  def listKeys(listObjectsRequest: ListObjectsRequest): Seq[String] = {
    var listing: ObjectListing = null
    val result = mutable.ListBuffer[String]()
    do {
      listing = client.listObjects(listObjectsRequest)
      // avoid including "directories" in the input split, can cause 403 errors on GET
      result ++= listing.getObjectSummaries.asScala.map(_.getKey).filterNot(_ endsWith "/")
      listObjectsRequest.setMarker(listing.getNextMarker)
    } while (listing.isTruncated)

    result.toSeq
  }

  def getStream(fileUri: String): InputStream = {
    val uri = new URI(fileUri)

    uri.getScheme match {
      case "file" =>
        new FileInputStream(new File(uri))
      case "http" =>
        uri.toURL.openStream
      case "https" =>
        uri.toURL.openStream
      case "s3" =>
        val client = new AmazonS3Client(new DefaultAWSCredentialsProviderChain)
        val s3uri = new AmazonS3URI(uri)
        client.getObject(s3uri.getBucket, s3uri.getKey).getObjectContent()
      case _ =>
        throw new IllegalArgumentException(s"Resource at $uri is not valid")
    }
  }

  def readFile(fileUri: String): String = {
    val is = getStream(fileUri)
    try {
      IOUtils.toString(is)
    } finally {
      is.close()
    }
  }
}
