package com.azavea.rf.api

import java.net.URL

import com.azavea.rf.common.S3
import com.azavea.rf.datamodel.ExportOptions
import java.time.Duration

package object exports {
  implicit def listUrlToListString(urls: List[URL]): List[String] = urls.map(_.toString)

  implicit class ExportOptionsMethods(exportOptions: ExportOptions) {
    def getSignedUrls(duration: Duration = Duration.ofDays(1)): List[URL] = {
      (exportOptions.source.getScheme match {
        case "s3" | "s3a" | "s3n" => Some(S3.getSignedUrls(exportOptions.source))
        case _ => None
      }).getOrElse(Nil)
    }
  }
}
