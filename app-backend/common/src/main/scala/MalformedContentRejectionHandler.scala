package com.azavea.rf.common

import akka.http.scaladsl.server._
import akka.http.scaladsl.model._
import StatusCodes._
import Directives._

import scala.util.matching.Regex

object RFRejectionHandler {
  implicit val rfRejectionHandler: RejectionHandler = RejectionHandler
    .newBuilder()
    .handle {
      case e: MalformedRequestContentRejection => {
        val missingFieldPattern =
          """(.*decode value.*)(?:DownField\()(.*)(?:\))""".r
        val badTypePattern = """(?:.*DownField\()(.*)(?:\))""".r
        e.getCause.getMessage match {
          case missingFieldPattern(missing, field) =>
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
