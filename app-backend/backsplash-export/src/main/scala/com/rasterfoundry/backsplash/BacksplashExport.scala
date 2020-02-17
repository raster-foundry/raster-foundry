package com.rasterfoundry.backsplash.export

import com.rasterfoundry.backsplash.export.ExportableInstances._
import com.rasterfoundry.backsplash.export.shapes._
import com.rasterfoundry.common.export._

import Exportable.ops._
import _root_.io.circe.parser._
import _root_.io.circe.shapes._
import cats.effect._
import cats.implicits._
import com.monovore.decline._
import com.typesafe.scalalogging._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.spark.io.s3.S3Client
import org.apache.commons.io.FileUtils
import shapeless._

import scala.concurrent.ExecutionContext

import java.io.File
import java.net.URI

/**
  * Point this command line utility at a serialized version of any type that
  *  has a valid [[Exportable]] instance; it will produce an appropriate tiff
  *  and upload said tiff to the desired output location
  *
  * To extend this program with a new type (perhaps sourcing raster data in
  *  some way not anticipated here), add a new typeclass instance for the new
  *  export definition, add the type in question to the BacksplashExport.Exports type below,
  *  and ensure that SerDe behaviors are provided as well
  */
object BacksplashExport
    extends CommandApp(
      name = "rf-export",
      header = "Export mosaic and analysis TIFs",
      main = {

        val exportDefOpt =
          Opts.option[URI](
            "definition",
            short = "d",
            help = "URI of export.ExportDefinition JSON"
          )

        val compressionLevelOpt = Opts
          .option[Int](
            "compression",
            short = "c",
            help = "The (deflate) compression level to apply on export"
          )
          .withDefault(9)

        (exportDefOpt, compressionLevelOpt).mapN {
          (exportDefUri, compressionLevel) =>
            implicit val cs: ContextShift[IO] =
              IO.contextShift(ExecutionContext.global)
            val logger = Logger[BacksplashExport.type]
            val exportDefString = UriReader.read(exportDefUri)

            // The Coproduct of case classes which have a valid `Exportable` instance available
            type Exports =
              ExportDefinition[AnalysisExportSource] :+:
                ExportDefinition[MosaicExportSource] :+:
                CNil

            decode[Exports](exportDefString) match {
              case Right(exportDef) =>
                logger.info(
                  s"Beginning tiff export to ${exportDef.exportDestination}."
                )
                val t0 = System.nanoTime()

                val compression = DeflateCompression(compressionLevel)
                val geotiff = exportDef.toGeoTiff(compression)
                val geotiffBytes = geotiff.toCloudOptimizedByteArray
                val destination = new URI(exportDef.exportDestination)
                destination.getScheme match {
                  case "s3" =>
                    val bucket = destination.getHost
                    val key = destination.getPath.tail
                    logger.info(s"Uploading tif to bucket: $bucket; key: $key")
                    S3Client.DEFAULT.putObject(bucket, key, geotiffBytes)
                  case "file" =>
                    logger.info(s"Writing tif to file: ${destination.getPath}")
                    FileUtils.writeByteArrayToFile(
                      new File(destination.getPath),
                      geotiffBytes
                    )
                  case scheme =>
                    sys.error(s"Unable to upload tif to scheme: $scheme")
                }

                val t1 = System.nanoTime()
                val secondsElapsed = (t1 - t0).toDouble / 1000000000
                val secondsString = "%.2f".format(secondsElapsed)
                logger.info(s"Export completed in $secondsString seconds")
              case Left(err) =>
                throw err
            }
        }
      }
    )
