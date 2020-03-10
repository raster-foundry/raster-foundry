package com.rasterfoundry.backsplash.export

import com.amazonaws.services.s3._
import scalaj.http._

import scala.io.Source

import java.io.{BufferedReader, InputStreamReader}
import java.net.URI
import java.util.stream.Collectors

object UriReader {

  private lazy val s3client = AmazonS3ClientBuilder.defaultClient()

  def read(uri: URI): String = uri.getScheme match {
    case "http" | "https" =>
      Http(uri.toString).method("GET").asString.body
    case "file" =>
      Source.fromFile(uri.getPath).getLines.mkString
    case "s3" =>
      val s3uri = new AmazonS3URI(uri)
      val objectIS = s3client
        .getObject(s3uri.getBucket, s3uri.getKey)
        .getObjectContent()
      // found this method for IS => String from: https://stackoverflow.com/questions/309424/how-do-i-read-convert-an-inputstream-into-a-string-in-java
      new BufferedReader(new InputStreamReader(objectIS))
        .lines()
        .collect(Collectors.joining("\n"));
    case _ =>
      throw new Exception(
        "A valid URI is required if you want to export data...")
  }
}
