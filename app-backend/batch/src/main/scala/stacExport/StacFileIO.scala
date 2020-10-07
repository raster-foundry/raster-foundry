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
import com.azavea.stac4s.{`image/cog`, StacAssetRole, StacItem}
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.syntax._

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import com.azavea.stac4s.StacItemAsset

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
  ): IO[ScalaFile] =
    IO {
      val absolutePathURI = new AmazonS3URI(stacWithAbsolute.absolutePath)
      val key = absolutePathURI.getKey
      val localOutputPath = s"${tempDir.path}/$key"
      val data = stacWithAbsolute.item
      val file = ScalaFile(localOutputPath)
      logger.debug(s"Writing to Local File System: $localOutputPath")
      file
        .createIfNotExists(false, true)
        .append(Printer.spaces2.copy(dropNullValues = true).print(data.asJson))
      file
    }

  def putToS3(path: String, file: ScalaFile): IO[PutObjectResult] =
    IO {
      logger.debug(s"Writing to S3: $path")
      val uri = new AmazonS3URI(path)
      s3Client.putObject(uri.getBucket, uri.getKey, file.toJava)
    }

  def putObjectToS3[A: Encoder](
      stacWithAbsolute: ObjectWithAbsolute[A]
  ): IO[PutObjectResult] =
    IO {
      val uri = new AmazonS3URI(stacWithAbsolute.absolutePath)
      val key = uri.getKey
      val dataByte = Printer.noSpaces
        .copy(dropNullValues = true)
        .print(stacWithAbsolute.item.asJson)
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

  def signTiffAsset(
      item: StacItem,
      assetLocation: String
  ): IO[StacItem] =
    IO { s3Client.maybeSignUri(assetLocation) } map { signedUrl =>
      val dataAssetFallback = StacItemAsset(
        signedUrl,
        None,
        None,
        Set(StacAssetRole.Data),
        Some(`image/cog`)
      )
      val dataAssetO = item.assets.get("data") map { asset =>
        asset.copy(href = signedUrl)
      }
      item.copy(
        assets = item.assets ++ Map(
          "data" -> (dataAssetO getOrElse dataAssetFallback)
        )
      )
    }
}
