package com.azavea.rf.api

import java.net.URI

import com.azavea.rf.common.S3

package object uploads {
    implicit def stringAsJavaURI(uri: String): URI = new URI(uri)

    def listAllowedFilesInS3Source(source: String): List[String] = {
        S3.getObjectPaths(source)
            .filter { p =>
                val _p = p.toLowerCase
                _p.endsWith(".tif") ||
                _p.endsWith(".tif")
            }
    }
}
