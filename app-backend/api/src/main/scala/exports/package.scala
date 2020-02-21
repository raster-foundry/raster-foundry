package com.rasterfoundry.api

import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.common.S3
import com.rasterfoundry.datamodel.{Export, ExportOptions, User}

import io.circe.syntax._

import java.net.{URI, URL, URLDecoder}
import java.nio.charset.StandardCharsets.UTF_8
import java.sql.Timestamp
import java.util.Date

package object exports extends Config {

  val s3Client = S3()

  implicit def listUrlToListString(urls: List[URL]): List[String] =
    urls.map(_.toString)

  implicit class ExportOptionsMethods(exportOptions: ExportOptions) {
    def getSignedUrls(): List[URL] = {
      (exportOptions.source.getScheme match {
        case "s3" | "s3a" | "s3n" =>
          Some(s3Client.getSignedUrls(exportOptions.source))
        case _ => None
      }).getOrElse(Nil)
    }

    def getObjectKeys(): List[String] = {
      (exportOptions.source.getScheme match {
        case "s3" | "s3a" | "s3n" =>
          Some(s3Client.getObjectKeys(exportOptions.source))
        case _ => None
      }).getOrElse(Nil)
    }

    def getSignedUrl(objectKey: String): URL = {
      val amazonURI = S3.createS3Uri(exportOptions.source + "/" + objectKey)
      val bucket: String = amazonURI.getBucket
      val key: String = URLDecoder.decode(amazonURI.getKey, UTF_8.toString())
      (exportOptions.source.getScheme match {
        case "s3" | "s3a" | "s3n" => Some(s3Client.getSignedUrl(bucket, key))
        case _                    => None
      }).getOrElse(new URL(""))
    }
  }

  implicit class UserMethods(user: User) {
    def createDefaultExportSource(export: Export): URI = {
      val uri = user.getDefaultExportSource(export, dataBucket)
      val amazonURI = S3.createS3Uri(uri)
      val (bucket, key) = amazonURI.getBucket -> amazonURI.getKey
      val now = new Timestamp(new Date().getTime)

      if (!s3Client.doesObjectExist(bucket, s"${key}/RFUploadAccessTestFile")) {
        s3Client.putObjectString(
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
