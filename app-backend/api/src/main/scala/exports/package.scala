package com.azavea.rf.api

import java.net.{URI, URL, URLDecoder}
import java.nio.charset.StandardCharsets.UTF_8
import java.sql.Timestamp
import java.util.Date

import com.amazonaws.services.s3.AmazonS3URI
import com.azavea.rf.api.utils.Config
import com.azavea.rf.common.S3
import com.azavea.rf.datamodel.{Export, ExportOptions, User}
import io.circe.syntax._

package object exports extends Config {
  implicit def listUrlToListString(urls: List[URL]): List[String] =
    urls.map(_.toString)

  implicit class ExportOptionsMethods(exportOptions: ExportOptions) {
    def getSignedUrls(): List[URL] = {
      (exportOptions.source.getScheme match {
        case "s3" | "s3a" | "s3n" =>
          Some(S3.getSignedUrls(exportOptions.source))
        case _ => None
      }).getOrElse(Nil)
    }

    def getObjectKeys(): List[String] = {
      (exportOptions.source.getScheme match {
        case "s3" | "s3a" | "s3n" =>
          Some(S3.getObjectKeys(exportOptions.source))
        case _ => None
      }).getOrElse(Nil)
    }

    def getSignedUrl(objectKey: String): URL = {
      val amazonURI = new AmazonS3URI(exportOptions.source + "/" + objectKey)
      val bucket: String = amazonURI.getBucket
      val key: String = URLDecoder.decode(amazonURI.getKey, UTF_8.toString())
      (exportOptions.source.getScheme match {
        case "s3" | "s3a" | "s3n" => Some(S3.getSignedUrl(bucket, key))
        case _                    => None
      }).getOrElse(new URL(""))
    }
  }

  implicit class UserMethods(user: User) {
    def createDefaultExportSource(export: Export): URI = {
      val uri = user.getDefaultExportSource(export, dataBucket)
      val amazonURI = new AmazonS3URI(uri)
      val (bucket, key) = amazonURI.getBucket -> amazonURI.getKey
      val now = new Timestamp(new Date().getTime)

      if (!S3.doesObjectExist(bucket, s"${key}/RFUploadAccessTestFile")) {
        S3.putObjectString(
          dataBucket,
          s"${key}/RFUploadAccessTestFile",
          s"Allow Upload Access for RF: ${key} at ${now.toString}"
        )
      }
      uri
    }

    def updateDefaultExportSource(export: Export): Export = {
      val exportOptions =
        export.getExportOptions.map { exportOptions =>
          val source: URI =
            exportOptions.source match {
              case uri if uri.toString.trim != "" => uri
              case _                              => createDefaultExportSource(export)
            }

          exportOptions.copy(source = source)
        }

      export.copy(exportOptions = exportOptions.asJson)
    }
  }

}
