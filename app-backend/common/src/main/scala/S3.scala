package com.rasterfoundry.common

import com.amazonaws.HttpMethod
import com.amazonaws.auth.{
  AWSCredentialsProvider,
  DefaultAWSCredentialsProviderChain
}
import com.amazonaws.regions._
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder, AmazonS3URI}
import jp.ne.opt.chronoscala.Imports._
import org.apache.commons.io.IOUtils

import scala.annotation.tailrec
import scala.collection.JavaConverters._

import java.io.File
import java.net._
import java.time.{Duration, ZoneOffset}
import java.util.Date

sealed trait S3Region
final case class S3RegionEnum(s3Region: Regions) extends S3Region
final case class S3RegionString(s3Region: String) extends S3Region

final case class S3(
    credentialsProviderChain: AWSCredentialsProvider =
      new DefaultAWSCredentialsProviderChain,
    region: Option[S3Region] = None
) extends Serializable
    with RollbarNotifier {

  lazy val client: AmazonS3 = region match {
    case Some(S3RegionEnum(region)) =>
      AmazonS3ClientBuilder
        .standard()
        .withRegion(region)
        .withForceGlobalBucketAccessEnabled(true)
        .build()
    case Some(S3RegionString(region)) =>
      AmazonS3ClientBuilder
        .standard()
        .withRegion(region)
        .withForceGlobalBucketAccessEnabled(true)
        .build()
    case _ =>
      AmazonS3ClientBuilder
        .standard()
        .withForceGlobalBucketAccessEnabled(true)
        .build()
  }

  lazy val transferManager = TransferManagerBuilder
    .standard()
    .withS3Client(client)
    .build()

  // we want to ignore here, because uri.getHost returns null instead of an Option[String] -- thanks Java
  @SuppressWarnings(Array("NullParameter"))
  def bucketAndPrefixFromURI(
      uri: URI,
      stripSlash: Boolean = true
  ): (String, String) = {
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

  def getObject(
      s3bucket: String,
      s3prefix: String,
      requesterPays: Boolean = false
  ): S3Object =
    client.getObject(new GetObjectRequest(s3bucket, s3prefix, requesterPays))

  def getSignedUrl(
      bucket: String,
      key: String,
      duration: Duration = Duration.ofDays(1),
      method: HttpMethod = HttpMethod.GET
  ): URL = {
    val expiration = LocalDateTime.now + duration
    val generatePresignedUrlRequest =
      new GeneratePresignedUrlRequest(bucket, key)
    generatePresignedUrlRequest.setMethod(method)
    generatePresignedUrlRequest.setExpiration(
      Date.from(expiration.toInstant(ZoneOffset.UTC))
    )
    client.generatePresignedUrl(generatePresignedUrlRequest)
  }

  def signUri(uriString: String, duration: Duration): String = {
    val s3Uri = new AmazonS3URI(URLDecoder.decode(uriString, "utf-8"))
    getSignedUrl(s3Uri.getBucket, s3Uri.getKey, duration).toString
  }

  def maybeSignUri(
      uriString: String,
      whitelist: List[String] = List(),
      duration: Duration = Duration.ofDays(1)
  ): String = {
    val whitelisted =
      whitelist.map(uriString.startsWith(_)).foldLeft(false)(_ || _)
    if (whitelisted) {
      signUri(uriString, duration)
    } else uriString
  }

  def getSignedUrls(
      source: URI,
      duration: Duration = Duration.ofDays(1),
      stripSlash: Boolean = true
  ): List[URL] = {
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

  def listObjects(listObjectsRequest: ListObjectsRequest): ObjectListing =
    client.listObjects(listObjectsRequest)

  def listObjects(uri: URI): ObjectListing = {
    val s3uri = new AmazonS3URI(uri)
    listObjects(s3uri.getBucket, s3uri.getKey)
  }

  def listObjects(bucketName: String, prefix: String): ObjectListing =
    listObjects(new ListObjectsRequest(bucketName, prefix, null, null, null))

  def putObjectString(bucket: String, key: String, contents: String): String = {
    client.putObject(bucket, key, contents)
    contents
  }

  def putObject(bucket: String, key: String, file: File) =
    client.putObject(bucket, key, file)

  def putObject(putObjectRequest: PutObjectRequest): PutObjectResult =
    client.putObject(putObjectRequest)

  def putObjectMultiPart(bucket: String, key: String, file: File) = {
    // TransferManager processes all transfers asynchronously,
    // so this call returns immediately.
    val upload = transferManager.upload(bucket, key, file)
    logger.info("Object upload started...")
    // Optionally, wait for the upload to finish before continuing.
    upload.waitForCompletion()
    logger.info("Object upload complete...")
  }

  def doesObjectExist(bucket: String, key: String): Boolean =
    client.doesObjectExist(bucket, key)

  def getS3Url(bucketName: String, key: String): URL =
    client.getUrl(bucketName, key)

  /** Copy buckets */
  @tailrec
  def copyListing(
      bucket: String,
      destBucket: String,
      sourcePrefix: String,
      destPrefix: String,
      listing: ObjectListing
  ): Unit = {
    listing.getObjectSummaries.asScala.foreach { os =>
      val key = os.getKey
      client.copyObject(
        bucket,
        key,
        destBucket,
        key.replace(sourcePrefix, destPrefix)
      )
    }
    if (listing.isTruncated)
      copyListing(
        bucket,
        destBucket,
        sourcePrefix,
        destPrefix,
        client.listNextBatchOfObjects(listing)
      )
  }

  def deleteObject(bucket: String, key: String): Unit = {
    client.deleteObject(bucket, key)
  }
}

object S3 {

  def getObjectMetadata(s3Object: S3Object): ObjectMetadata =
    s3Object.getObjectMetadata

  def getObjectBytes(s3Object: S3Object): Array[Byte] = {
    val s3InputStream = s3Object.getObjectContent
    try IOUtils.toByteArray(s3InputStream)
    finally s3InputStream.close()
  }

  def createS3Uri(uri: String): AmazonS3URI = new AmazonS3URI(uri)

  def createS3Uri(uri: URI): AmazonS3URI = new AmazonS3URI(uri)

}
