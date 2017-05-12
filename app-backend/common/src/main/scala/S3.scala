package com.azavea.rf.common

import scala.util.{Success, Failure, Try}

import org.apache.commons.io.IOUtils

import com.amazonaws.auth._
import com.amazonaws.services.s3.{AmazonS3ClientBuilder, AmazonS3URI}
import com.amazonaws.services.s3.model.{S3Object, ObjectMetadata}

import java.io._
import java.net._

package object S3 {
  def getObject(uri: URI): S3Object = {
    val client = AmazonS3ClientBuilder.standard()
      .withCredentials(new DefaultAWSCredentialsProviderChain())
      .build()
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
    val client = AmazonS3ClientBuilder.standard()
      .withCredentials(new DefaultAWSCredentialsProviderChain())
      .build()

    Try(client.putObject(bucket, key, contents)) match {
      case Success(_) => contents
      case Failure(ex) => throw new Exception(ex)
    }
  }
}
