package com.azavea.rf.export.util

import geotrellis.spark.io.s3.S3InputFormat

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import org.apache.hadoop.conf.Configuration

import java.net.URI

object S3 {
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
