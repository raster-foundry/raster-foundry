package com.azavea.rf.ingest.util

import com.amazonaws.auth._
import com.amazonaws.services.s3.model.{ListObjectsRequest, ObjectListing}
import com.amazonaws.services.s3.{AmazonS3Client, AmazonS3URI}
import geotrellis.spark.io.s3.S3LayerWriter
import org.apache.commons.io.IOUtils
import org.apache.spark._
import org.apache.spark.rdd.RDD

import java.io._
import java.net._
import java.util.HashMap
import java.util._
import java.net.URI
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.matching.Regex

object S3 {
  val cred = new DefaultAWSCredentialsProviderChain()
  val client = new AmazonS3Client(cred)

  // From GT codebase
  private val idRx = "[A-Z0-9]{20}"
  private val keyRx = "[a-zA-Z0-9+/]+={0,2}"
  private val slug = "[a-zA-Z0-9-]+"
  val S3UrlRx = new Regex(s"""s3[an]?://(?:($idRx):($keyRx)@)?($slug)/{0,1}(.*)""", "aws_id", "aws_key", "bucket", "prefix")

  /** List the keys to files found within a given bucket */
  def listKeys(url: String, ext: String, recursive: Boolean): Array[URI] = {
    val S3UrlRx(_, _, bucket, prefix) = url
    listKeys(bucket, prefix, ext, recursive)
  }

  /** List the keys to files found within a given bucket */
  def listKeys(s3bucket: String, s3prefix: String, ext: String, recursive: Boolean = false): Array[URI] = {
    val objectRequest = (new ListObjectsRequest)
      .withBucketName(s3bucket)
      .withPrefix(s3prefix)

    if (! recursive) { // Avoid digging into a deeper directory
      objectRequest.withDelimiter("/")
    }

    listKeys(objectRequest)
      .collect { case key if key.endsWith(ext) => new URI(s"s3://${s3bucket}/${key}") }.toArray
  }

  /** List the keys to files found within a given bucket.
    *  (copied from GeoTrellis codebase)
    */
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

  /** Parse an S3 URI unto its bucket and prefix portions */
  def parse(uri: URI): (String, String) = {
    val S3UrlRx(_, _, bucket, prefix) = uri.toString
    (bucket, prefix)
  }
}
