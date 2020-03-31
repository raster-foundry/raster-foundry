package com.rasterfoundry.batch.stacExport

import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.S3

import better.files.{File => ScalaFile}
import cats.effect.IO
import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.{
  ObjectMetadata,
  PutObjectRequest,
  PutObjectResult
}
import com.typesafe.scalalogging.LazyLogging
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.server.stac.StacItem
import io.circe._
import io.circe.syntax._

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

case class ObjectWithAbsolute[A](absolutePath: String, item: A)

case class SceneItemWithAbsolute(
    item: ObjectWithAbsolute[StacItem],
    ingestLocation: String
)

object StacFileIO extends LazyLogging with Config {

  protected def s3Client = S3()

  def writeObjectToFilesystem[A: Encoder](
      tempDir: ScalaFile,
      stacWithAbsolute: ObjectWithAbsolute[A]
  ): IO[ScalaFile] = IO {
    val absolutePathURI = new AmazonS3URI(stacWithAbsolute.absolutePath)
    val key = absolutePathURI.getKey
    val localOutputPath = s"${tempDir.path}/$key"
    val data = stacWithAbsolute.item
    val file = ScalaFile(localOutputPath)
    logger.debug(s"Writing to Local File System: $localOutputPath")
    file
      .createIfNotExists(false, true)
      .append(Printer.spaces2.copy(dropNullValues = true).pretty(data.asJson))
    file
  }

  def putToS3(path: String, file: ScalaFile): IO[PutObjectResult] = IO {
    logger.debug(s"Writing to S3: $path")
    val uri = new AmazonS3URI(path)
    s3Client.putObject(uri.getBucket, uri.getKey, file.toJava)
  }

  def getObject(
      tempDir: ScalaFile,
      stacWithAbsolute: ObjectWithAbsolute[StacItem],
      ingestLocation: String
  ): IO[Unit] = {
    val absolutePathURI = new AmazonS3URI(stacWithAbsolute.absolutePath)
    val key = absolutePathURI.getKey
    val tiffKey =
      (key.split("/").dropRight(1) :+ s"${stacWithAbsolute.item.id}.tiff")
        .mkString("/")
    val localPath = s"${tempDir.path}/$tiffKey"
    logger.debug(s"Fetching $ingestLocation to $localPath")
    IO { GeoTiffRasterSource(ingestLocation) } map { rs =>
      rs.tiff.write(localPath)
    }
  }

  def putObjectToS3[A: Encoder](
      stacWithAbsolute: ObjectWithAbsolute[A]
  ): IO[PutObjectResult] = IO {
    val uri = new AmazonS3URI(stacWithAbsolute.absolutePath)
    val key = uri.getKey
    val dataByte = Printer.noSpaces
      .copy(dropNullValues = true)
      .pretty(stacWithAbsolute.item.asJson)
      .getBytes(Charset.forName("UTF-8"))
    val dataStream = new ByteArrayInputStream(dataByte)
    val dataMd = new ObjectMetadata()
    dataMd.setContentType("application/json")
    dataMd.setContentLength(dataByte.length)
    logger.debug(s"Writing ${dataByte.length} bytes to $key")
    s3Client.putObject(
      new PutObjectRequest(dataBucket, key, dataStream, dataMd)
    )
  }
}
