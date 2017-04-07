package com.azavea.rf.common

import akka.http.scaladsl.server._
import akka.http.scaladsl.model._
import StatusCodes._
import Directives._

import scala.util.matching.Regex

object RFRejectionHandler {
  implicit val rfRejectionHandler:RejectionHandler = RejectionHandler.newBuilder()
    .handle {
    case e: MalformedRequestContentRejection => {
      val missingFieldPattern = """(?:DownField\()(.*)(?:\))""".r
      missingFieldPattern findFirstMatchIn e.message match {
        case Some(matchedGroup) =>
          complete(ClientError(400)("Bad Request", s"Missing field ${matchedGroup.group(1)}"))
        case _ => complete(ClientError(400)("Bad Request", e.message))
      }
    }
  }
  .result()
}
