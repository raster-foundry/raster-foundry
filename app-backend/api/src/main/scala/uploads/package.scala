package com.rasterfoundry.api

import java.net.URI

import com.rasterfoundry.common.S3

package object uploads {
  implicit def stringAsJavaURI(uri: String): URI = new URI(uri)

  def listAllowedFilesInS3Source(source: String): List[String] = {
    val s3Client = S3()
    s3Client
      .getObjectPaths(source, false)
      .filter { p =>
        val _p = p.toLowerCase
        _p.endsWith(".tif") ||
        _p.endsWith(".tiff")
      }
  }
}
