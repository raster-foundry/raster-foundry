package com.azavea.rf.api

import io.circe._
import io.circe.syntax._

import com.azavea.rf.common.S3
import com.azavea.rf.datamodel.{Export, ExportOptions, User}
import com.azavea.rf.api.utils.Config
import com.amazonaws.services.s3.AmazonS3URI

import java.time.Duration
import java.util.Date
import java.net.{URI, URL}
import java.sql.Timestamp

package object exports extends Config {
  implicit def listUrlToListString(urls: List[URL]): List[String] = urls.map(_.toString)

  implicit class ExportOptionsMethods(exportOptions: ExportOptions) {
    def getSignedUrls(duration: Duration = Duration.ofDays(1)): List[URL] = {
      (exportOptions.source.getScheme match {
        case "s3" | "s3a" | "s3n" => Some(S3.getSignedUrls(exportOptions.source))
        case _ => None
      }).getOrElse(Nil)
    }
  }

  implicit class UserMethods(user: User) {
    def createDefaultExportSource(export: Export): URI = {
      val uri = user.getDefaultExportSource(export, dataBucket)
      val amazonURI = new AmazonS3URI(uri)
      val (bucket, key) = amazonURI.getBucket -> amazonURI.getKey
      val now = new Timestamp(new Date().getTime)

      if (!S3.doesObjectExist(bucket, s"${key}/RFUploadAccessTestFile")) {
        S3.putObject(
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
              case _ => createDefaultExportSource(export)
            }

          exportOptions.copy(source = source)
        }

      export.copy(exportOptions = exportOptions.asJson)
    }
  }
}
