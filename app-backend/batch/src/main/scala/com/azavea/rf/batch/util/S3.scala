package com.azavea.rf.batch.util

import java.net.URI

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{ListObjectsRequest, ObjectListing, S3Object}
import geotrellis.spark.io.s3.S3InputFormat
import org.apache.hadoop.conf.Configuration

import scala.collection.mutable
import scala.collection.JavaConverters._

object S3 {
  lazy val client = AmazonS3ClientBuilder.standard()
    .withCredentials(new DefaultAWSCredentialsProviderChain())
    .build()

  /** Get S3Object */
  def getObject(s3bucket: String, s3prefix: String): S3Object =
    client.getObject(s3bucket, s3prefix)

  /** List the keys to files found within a given bucket */
  def listKeys(url: String, ext: String, recursive: Boolean): Array[URI] = {
    val S3InputFormat.S3UrlRx(_, _, bucket, prefix) = url
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
    val S3InputFormat.S3UrlRx(_, _, bucket, prefix) = uri.toString
    (bucket, prefix)
  }

  /** Set credentials in case Hadoop configuration files don't specify S3 credentials. */
  def setCredentials(conf: Configuration): Configuration = {
    val pc = new DefaultAWSCredentialsProviderChain

    conf.set("fs.s3.impl", classOf[org.apache.hadoop.fs.s3native.NativeS3FileSystem].getName)
    conf.set("fs.s3.awsAccessKeyId", pc.getCredentials.getAWSAccessKeyId)
    conf.set("fs.s3.awsSecretAccessKey", pc.getCredentials.getAWSSecretKey)
    conf
  }
}
