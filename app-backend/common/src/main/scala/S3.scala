package com.azavea.rf.common

import scala.util.{Success, Failure, Try}

import org.apache.commons.io.IOUtils

import com.amazonaws.auth._
import com.amazonaws.regions._
import com.amazonaws.services.s3.{AmazonS3ClientBuilder, AmazonS3URI}
import com.amazonaws.services.s3.model.{S3Object, ObjectMetadata}

import java.io._
import java.net._

package object S3 {
  def client = AmazonS3ClientBuilder.standard()
    .withCredentials(new DefaultAWSCredentialsProviderChain())
    .withRegion(Regions.US_EAST_1)
    .build()

  def getObject(uri: URI): S3Object = {
    val s3uri = new AmazonS3URI(uri)
    Try(client.getObject(s3uri.getBucket, s3uri.getKey)) match {
      case Success(o) => o
      case Failure(ex) => throw new Exception(ex)
    }
  }

  def getObjectMetadata(s3Object: S3Object): ObjectMetadata = {
    s3Object.getObjectMetadata()
  }

  def getObjectBytes(s3Object: S3Object): Array[Byte] = {
    val s3InputStream = s3Object.getObjectContent()

    try {
      IOUtils.toByteArray(s3InputStream)
    } finally {
      s3InputStream.close()
    }
  }

  def putObject(bucket: String, key: String, contents: String): String = {
    client.putObject(bucket, key, contents)
    contents
  }
}
