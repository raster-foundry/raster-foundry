package com.azavea.rf.batch.util

import java.net.URI

import com.amazonaws.auth.{
  AWSCredentialsProvider,
  DefaultAWSCredentialsProviderChain
}
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder, AmazonS3URI}
import geotrellis.spark.io.s3.S3InputFormat
import org.apache.commons.io.IOUtils
import org.apache.hadoop.conf.Configuration

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable

final case class S3(
    credentialsProviderChain: AWSCredentialsProvider =
      new DefaultAWSCredentialsProviderChain,
    region: Option[String] = None
) extends Serializable {

  lazy val client: AmazonS3 = {
    val builder =
      AmazonS3ClientBuilder
        .standard()
        .withCredentials(credentialsProviderChain)

    region.fold(builder)(builder.withRegion).build()
  }

  /** Copy buckets */
  @tailrec
  def copyListing(bucket: String,
                  destBucket: String,
                  sourcePrefix: String,
                  destPrefix: String,
                  listing: ObjectListing): Unit = {
    listing.getObjectSummaries.asScala.foreach { os =>
      val key = os.getKey
      client.copyObject(bucket,
                        key,
                        destBucket,
                        key.replace(sourcePrefix, destPrefix))
    }
    if (listing.isTruncated)
      copyListing(bucket,
                  destBucket,
                  sourcePrefix,
                  destPrefix,
                  client.listNextBatchOfObjects(listing))
  }

  def listObjects(bucketName: String, prefix: String): ObjectListing =
    listObjects(new ListObjectsRequest(bucketName, prefix, null, null, null))

  def listObjects(listObjectsRequest: ListObjectsRequest): ObjectListing =
    client.listObjects(listObjectsRequest)

  /** Get S3Object */
  def getObject(s3bucket: String,
                s3prefix: String,
                requesterPays: Boolean = false): S3Object =
    client.getObject(new GetObjectRequest(s3bucket, s3prefix, requesterPays))

  def getObject(uri: URI): S3Object = {
    val s3uri = new AmazonS3URI(uri)
    getObject(s3uri.getBucket, s3uri.getKey)
  }

  def getObjectMetadata(s3Object: S3Object): ObjectMetadata =
    s3Object.getObjectMetadata

  def getObjectBytes(s3Object: S3Object): Array[Byte] = {
    val s3InputStream = s3Object.getObjectContent
    try IOUtils.toByteArray(s3InputStream)
    finally s3InputStream.close()
  }

  def putObject(s3bucket: String,
                s3Key: String,
                content: String): PutObjectResult =
    client.putObject(s3bucket, s3Key, content)

  def putObject(putObjectRequest: PutObjectRequest): PutObjectResult =
    client.putObject(putObjectRequest)

  def putObject(uri: URI, content: String): PutObjectResult = {
    val s3uri = new AmazonS3URI(uri)
    client.putObject(s3uri.getBucket, s3uri.getKey, content)
  }

  /** List the keys to files found within a given bucket */
  def listKeys(url: String, ext: String, recursive: Boolean): Array[URI] = {
    val S3InputFormat.S3UrlRx(_, _, bucket, prefix) = url
    listKeys(bucket, prefix, ext, recursive)
  }

  /** List the keys to files found within a given bucket */
  def listKeys(s3bucket: String,
               s3prefix: String,
               ext: String,
               recursive: Boolean = false,
               requesterPays: Boolean = false): Array[URI] = {
    val objectRequest = (new ListObjectsRequest)
      .withBucketName(s3bucket)
      .withPrefix(s3prefix)
      .withMaxKeys(1000)
      .withRequesterPays(requesterPays)

    // Avoid digging into a deeper directory
    if (!recursive) objectRequest.withDelimiter("/")

    listKeys(objectRequest).collect {
      case key if key.endsWith(ext) => new URI(s"s3://${s3bucket}/${key}")
    }.toArray
  }

  /** List the keys to files found within a given bucket.
    * (copied from GeoTrellis codebase)
    */
  @SuppressWarnings(Array("NullAssignment")) // copied from GeoTrellis so ignoring null assignment
  def listKeys(listObjectsRequest: ListObjectsRequest): Seq[String] = {
    var listing: ObjectListing = null
    val result = mutable.ListBuffer[String]()
    do {
      listing = client.listObjects(listObjectsRequest)
      // avoid including "directories" in the input split, can cause 403 errors on GET
      result ++= listing.getObjectSummaries.asScala
        .map(_.getKey)
        .filterNot(_ endsWith "/")
      listObjectsRequest.setMarker(listing.getNextMarker)
    } while (listing.isTruncated)

    result
  }
}

object S3 {

  /** Parse an S3 URI unto its bucket and prefix portions */
  def parse(uri: URI): (String, String) = {
    val S3InputFormat.S3UrlRx(_, _, bucket, prefix) = uri.toString
    (bucket, prefix)
  }

  /** Set credentials in case Hadoop configuration files don't specify S3 credentials. */
  def setCredentials(
      conf: Configuration,
      credentialsProviderChain: AWSCredentialsProvider =
        new DefaultAWSCredentialsProviderChain): Configuration = {

    /**
      * Identify whether function is called on EMR
      * fs.AbstractFileSystem.s3a.impl is a specific key which should be set on EMR
      *
      **/
    if (conf.isKeyUnset("fs.AbstractFileSystem.s3a.impl")) {
      conf.set(
        "fs.s3.impl",
        classOf[org.apache.hadoop.fs.s3native.NativeS3FileSystem].getName)
      conf.set("fs.s3.awsAccessKeyId",
               credentialsProviderChain.getCredentials.getAWSAccessKeyId)
      conf.set("fs.s3.awsSecretAccessKey",
               credentialsProviderChain.getCredentials.getAWSSecretKey)
    }
    conf
  }
}
