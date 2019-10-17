package com.rasterfoundry.batch.geojsonImport
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.S3

import com.typesafe.scalalogging.LazyLogging

import doobie.ConnectionIO
import doobie.implicits._
import cats.implicits._
import cats.effect._

import scala.util._
import java.util.UUID
import java.util.concurrent.Executors

import com.google.common.util.concurrent.ThreadFactoryBuilder

import java.util.UUID
import com.rasterfoundry.database.GeojsonUploadDao
import com.amazonaws.services.s3.AmazonS3URI
import java.net.URLDecoder

import java.nio.charset.StandardCharsets
import io.circe.parser.decode
import com.rasterfoundry.database.UserDao
import com.rasterfoundry.database.AnnotationDao

object CommandLine {
  final case class Params(
      upload: UUID = UUID.randomUUID(),
      testRun: Boolean = false
  )

  val parser =
    new scopt.OptionParser[Params]("raster-foundry-geojson-import") {
      override def terminate(exitState: Either[String, Unit]): Unit = ()

      head("raster-foundry-geojson-import", "0.1")

      opt[Unit]('t', "test")
        .action(
          (_, conf) => conf.copy(testRun = true)
        )
        .text("Dry run geojson import to verify created annotations")

      opt[String]('u', "upload")
        .required()
        .action(
          (u, conf) => {
            conf.copy(upload = UUID.fromString(u))
          }
        )
        .text("GeojsonUpload entry to process")
    }
}

object ImportGeojsonFiles extends Config with LazyLogging {
  val name = "import_geojson_files"

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("geojson-import-%d").build()
        )
      )
    )
  implicit val xa = RFTransactor.buildTransactor()

  def processUploadToAnnotations(
      upload: GeojsonUpload
  ): List[Annotation.Create] = {
    val s3Client = S3()
    // download and parse files
    upload.files.map { uri =>
      {
        logger.info(s"Downloading file: ${uri}")
        // Only s3 uris are currently supported.
        // TODO: hmm, this might be a security problem. Anyone can point RF at geojson file that we have
        // access to, and this will let them view it even if they shouldn't be able to view it.
        // I think we need a more formal system which requires signed uploads to specific directories, and
        // reject any s3 urls that aren't in those directories like scene uploads
        val s3Uri = new AmazonS3URI(URLDecoder.decode(uri, "utf-8"))
        val s3Object = s3Client.getObject(s3Uri.getBucket, s3Uri.getKey)
        val geojsonString =
          new String(S3.getObjectBytes(s3Object), StandardCharsets.UTF_8)
        logger.info("Annotations downloaded")
        logger.info("Parsing annotations")
        val annotations =
          decode[AnnotationFeatureCollectionCreate](geojsonString) match {
            case Right(fc) => fc.features.map(_.toAnnotationCreate).toList
            case Left(e)   => throw e
          }
        logger.info("Annotations parsed")
        annotations
      }
    }.flatten
  }

  def insertAnnotations(
      annotations: List[Annotation.Create],
      upload: GeojsonUpload
  ): ConnectionIO[List[Annotation]] = {
    // https://makk.es/blog/postgresql-parameter-limitation/
    // max # of interpolated params in postgres driver = 32,767 (2 byte int)
    // each annotation = 17 params
    // 32,767 / 17 = 1927 annotations / batch. Cut that in half to 1000 to be safe.
    for {
      user <- UserDao.unsafeGetUserById(upload.createdBy)
      inserted <- annotations
        .grouped(1000)
        .toList
        .traverse(annotationBatch => {
          val updatedAnnotationBatch =
            annotationBatch
              .map(_.copy(annotationGroup = Some(upload.annotationGroup)))
          AnnotationDao.insertAnnotations(
            updatedAnnotationBatch,
            upload.projectId,
            user,
            Some(upload.projectLayerId)
          )
        })
        .map { _.flatten }
    } yield inserted
  }

  def main(args: Array[String]): Unit = {
    val params = CommandLine.parser.parse(args, CommandLine.Params()) match {
      case Some(params) =>
        params
      case None =>
        logger.info("Invalid params")
        return
    }
    val uploadId = params.upload
    val uploadO = GeojsonUploadDao
      .getUploadById(uploadId)
      .transact(xa)
      .unsafeRunSync()

    uploadO match {
      case Some(upload) =>
        val annotations = processUploadToAnnotations(upload)
        logger.info(
          s"Uploading ${annotations.size} annotations to annotation group: ${upload.annotationGroup}"
        )
        val inserted =
          insertAnnotations(annotations, upload).transact(xa).unsafeRunSync()
        logger.info(s"Annotations uploaded: ${inserted.size}")
        return
      case _ =>
        logger.error(s"Unable to fetch upload with id: ${uploadId}")
        return
    }
  }
}
