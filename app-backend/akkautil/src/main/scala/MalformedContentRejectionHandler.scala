package com.rasterfoundry.akkautil

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

object RFRejectionHandler {
  implicit val rfRejectionHandler: RejectionHandler = RejectionHandler
    .newBuilder()
    .handle {
      case e: MalformedRequestContentRejection => {
        val missingFieldPattern =
          """(.*decode value.*)(?:DownField\()(.*)(?:\))""".r
        val badTypePattern = """(?:.*DownField\()(.*)(?:\))""".r
        e.getCause.getMessage match {
          case missingFieldPattern(_, field) =>
            complete(ClientError(400)("Bad Request", s"Missing field: $field"))
          case badTypePattern(field) =>
            complete(
              ClientError(400)(
                "Bad Request",
                s"Field cannot be parsed to expected type: $field"))
          case _ =>
            complete(ClientError(400)("Bad Request", e.getCause.getMessage))
        }
      }
    }
    .result()
}
