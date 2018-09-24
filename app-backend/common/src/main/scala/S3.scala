package com.azavea.rf.common

import jp.ne.opt.chronoscala.Imports._
import org.apache.commons.io.IOUtils
import com.amazonaws.auth._
import com.amazonaws.regions._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder, AmazonS3URI}
import com.amazonaws.services.s3.model.{
  ListObjectsRequest,
  ObjectListing,
  ObjectMetadata,
  S3Object
}
import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import java.io._
import java.net._
import java.time.{Duration, ZoneOffset}
import java.util.Date

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Properties

package object S3 {

  lazy val client = AmazonS3ClientBuilder
    .standard()
    .withCredentials(new DefaultAWSCredentialsProviderChain())
    .withRegion(Regions.US_EAST_1)
    .build()

  def clientWithRegion(region: Regions): AmazonS3 =
    AmazonS3ClientBuilder
      .standard()
      .withRegion(region)
      .build()

  // we want to ignore here, because uri.getHost returns null instead of an Option[String] -- thanks Java
  @SuppressWarnings(Array("NullParameter"))
  def bucketAndPrefixFromURI(uri: URI,
                             stripSlash: Boolean = true): (String, String) = {
    val prefix = uri.getPath match {
      case ""                         => ""
      case "/"                        => ""
      case p if !p.tail.endsWith("/") => s"${p.tail}/"
      case p                          => p.tail
    }

    val bucket = (uri.getHost, uri.getAuthority) match {
      case (null, authority) => authority
      case (host, _)         => host
      case _                 => throw new IllegalStateException(s"Ambiguous bucket parse: $uri")
    }

    val prefixSanitized =
      if (stripSlash) prefix.replaceAll("/\\z", "") else prefix

    (bucket.replace(".s3.amazonaws.com", ""), prefixSanitized)
  }

  def getObject(uri: URI): S3Object = {
    val s3uri = new AmazonS3URI(uri)
    client.getObject(s3uri.getBucket, s3uri.getKey)
  }

  def getObjectMetadata(s3Object: S3Object): ObjectMetadata =
    s3Object.getObjectMetadata

  def getObjectBytes(s3Object: S3Object): Array[Byte] = {
    val s3InputStream = s3Object.getObjectContent
    try IOUtils.toByteArray(s3InputStream)
    finally s3InputStream.close()
  }

  def getSignedUrl(bucket: String,
                   key: String,
                   duration: Duration = Duration.ofDays(1)): URL = {
    val expiration = LocalDateTime.now + duration
    val generatePresignedUrlRequest =
      new GeneratePresignedUrlRequest(bucket, key)
    generatePresignedUrlRequest.setMethod(HttpMethod.GET)
    generatePresignedUrlRequest.setExpiration(
      Date.from(expiration.toInstant(ZoneOffset.UTC)))
    client.generatePresignedUrl(generatePresignedUrlRequest)
  }

  def getSignedUrls(source: URI,
                    duration: Duration = Duration.ofDays(1),
                    stripSlash: Boolean = true): List[URL] = {
    @tailrec
    def get(listing: ObjectListing, accumulator: List[URL]): List[URL] = {
      def getObjects: List[URL] =
        listing.getObjectSummaries.asScala.toList
          .filterNot(_.getKey.endsWith("/"))
          .map { os =>
            val (bucket, key) = os.getBucketName -> os.getKey
            getSignedUrl(bucket, key, duration)
          } ::: accumulator

      if (!listing.isTruncated) getObjects
      else get(client.listNextBatchOfObjects(listing), getObjects)
    }

    val (bucket, prefix) = bucketAndPrefixFromURI(source, stripSlash)

    val listObjectsRequest =
      new ListObjectsRequest()
        .withBucketName(bucket)
        .withPrefix(prefix)
        .withDelimiter("/")

    get(client.listObjects(listObjectsRequest), Nil)
  }

  def getObjectKeys(source: URI, stripSlash: Boolean = true): List[String] = {
    @tailrec
    def get(listing: ObjectListing, accumulator: List[String]): List[String] = {
      def getObjects: List[String] =
        listing.getObjectSummaries.asScala.toList
          .filterNot(_.getKey.endsWith("/"))
          .map(_.getKey.split("/").last) ::: accumulator

      if (!listing.isTruncated) getObjects
      else get(client.listNextBatchOfObjects(listing), getObjects)
    }

    val (bucket, prefix) = bucketAndPrefixFromURI(source, stripSlash)

    val listObjectsRequest =
      new ListObjectsRequest()
        .withBucketName(bucket)
        .withPrefix(s"${prefix}/")
        .withDelimiter("/")

    get(client.listObjects(listObjectsRequest), Nil)
  }

  def getObjectPaths(source: URI, stripSlash: Boolean = true): List[String] = {
    @tailrec
    def get(listing: ObjectListing, accumulator: List[String]): List[String] = {
      def getObjects: List[String] =
        listing.getObjectSummaries.asScala.toList
          .filterNot(_.getKey.endsWith("/"))
          .map(os => "s3://" + os.getBucketName + "/" + os.getKey) ::: accumulator

      if (!listing.isTruncated) getObjects
      else get(client.listNextBatchOfObjects(listing), getObjects)
    }

    val (bucket, prefix) = bucketAndPrefixFromURI(source, stripSlash)

    val listObjectsRequest =
      new ListObjectsRequest()
        .withBucketName(bucket)
        .withPrefix(prefix)
        .withDelimiter("/")

    get(client.listObjects(listObjectsRequest), Nil)
  }

  def listObjects(uri: URI): ObjectListing = {
    val s3uri = new AmazonS3URI(uri)
    listObjects(s3uri.getBucket, s3uri.getKey)
  }

  def listObjects(bucketName: String, prefix: String): ObjectListing =
    listObjects(new ListObjectsRequest(bucketName, prefix, null, null, null))

  def listObjects(listObjectsRequest: ListObjectsRequest): ObjectListing =
    client.listObjects(listObjectsRequest)

  def putObjectString(bucket: String, key: String, contents: String): String = {
    client.putObject(bucket, key, contents)
    contents
  }

  def putObject(bucket: String, key: String, file: File) =
    client.putObject(bucket, key, file)

  def doesObjectExist(bucket: String, key: String): Boolean =
    client.doesObjectExist(bucket, key)

  def maybeSignUri(uriString: String,
                   whitelist: List[String] = List()): String = {
    val whitelisted =
      whitelist.map(uriString.startsWith(_)).foldLeft(false)(_ || _)
    if (whitelisted) {
      val s3Uri = new AmazonS3URI(URLDecoder.decode(uriString, "utf-8"))
      S3.getSignedUrl(s3Uri.getBucket, s3Uri.getKey).toString
    } else uriString
  }
}
