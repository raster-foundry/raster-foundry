package com.rasterfoundry.batch.geojsonImport
import com.rasterfoundry.batch.Job
import com.rasterfoundry.common.S3
import com.rasterfoundry.database.AnnotationDao
import com.rasterfoundry.database.GeojsonUploadDao
import com.rasterfoundry.database.UserDao
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel._

import cats.effect._
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3URI
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import doobie.implicits._
import io.circe.parser.decode

import scala.util._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.UUID

object ImportGeojsonFiles extends Job with LazyLogging {
  val name = "import_geojson_files"

  val xa = RFTransactor.nonHikariTransactor(RFTransactor.TransactorConfig())

  def processUploadToAnnotations(
      upload: GeojsonUpload
  ): List[Annotation.Create] = {
    val s3Client = S3()
    // download and parse files
    upload.files.flatMap { uri =>
      {
        logger.info(s"Downloading file: ${uri}")
        // Only s3 uris are currently supported.
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
        logger.info(s"${annotations.size} annotations parsed")
        annotations
      }
    }
  }

  def insertAnnotations(
      annotations: List[Annotation.Create],
      upload: GeojsonUpload
  ): ConnectionIO[Int] = {
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
          AnnotationDao
            .insertAnnotations(
              updatedAnnotationBatch,
              upload.projectId,
              user,
              Some(upload.projectLayerId)
            )
            .map(_.size)
        })
        .map { _.foldLeft(0)(_ + _) }
    } yield inserted
  }

  def runJob(args: List[String]): IO[Unit] = {
    val uploadIdO = args.headOption.map(UUID.fromString(_))
    for {
      uploadO <- uploadIdO match {
        case Some(id) => GeojsonUploadDao.getUploadById(id).transact(xa)
        case _        => Option.empty.pure[IO]
      }
      inserts <- uploadO match {
        case Some(upload) =>
          val annotations = processUploadToAnnotations(upload)
          insertAnnotations(annotations, upload).transact(xa)
        case _ =>
          throw new RuntimeException(
            s"No geojson upload found with id: ${uploadIdO}"
          )
      }
    } yield {
      logger.info(s"Uploaded ${inserts} annotations")
    }
  }
}
