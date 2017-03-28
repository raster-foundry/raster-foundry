package com.azavea.rf.api.upload

import java.util.UUID

import akka.http.scaladsl.server.Route
import com.azavea.rf.common.{Authentication, STS, UserErrorHandler}
import com.azavea.rf.database.Database
import com.azavea.rf.api.utils.Config


trait UploadRoutes extends Authentication
  with Config
  with UserErrorHandler {

  implicit def database: Database

  val uploadRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix(JavaUUID) { uploadId =>
      pathPrefix("credentials") {
        pathEndOrSingleSlash {
          get { getUploadCredentials(uploadId) }
        }
      }
    }
  }

  def getUploadCredentials(uploadId: UUID): Route = authenticate { _ =>
    complete(STS.getCredentialsForUpload(uploadId.toString, dataBucket))
  }
}
