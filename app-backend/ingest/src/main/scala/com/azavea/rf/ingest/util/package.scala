package com.azavea.rf.ingest

import com.amazonaws.auth._
import com.amazonaws.services.s3.model.{ListObjectsRequest, ObjectListing}
import com.amazonaws.services.s3.{AmazonS3Client, AmazonS3URI}
import org.apache.commons.io.IOUtils
import org.apache.spark._
import org.apache.spark.rdd.RDD

import java.io._
import java.net._
import java.util._

package object util {

  def getStream(uri: URI): InputStream = uri.getScheme match {
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

  def readBytes(fileUri: URI): Array[Byte] = {
    val is = getStream(fileUri)
    try {
      IOUtils.toByteArray(is)
    } finally {
      is.close()
    }
  }

  def readString(fileUri: URI): String = {
    val is = getStream(fileUri)
    try {
      IOUtils.toString(is)
    } finally {
      is.close()
    }
  }
}
