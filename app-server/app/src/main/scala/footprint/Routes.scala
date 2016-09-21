package com.azavea.rf.footprint


import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.utils.{Database, UserErrorHandler}


trait FootprintRoutes extends Authentication with PaginationDirectives with UserErrorHandler{

  implicit def database: Database
  implicit val ec: ExecutionContext

  def footprintRoutes:Route = {
    handleExceptions(userExceptionHandler) {
      authenticate { user =>
        pathPrefix("api" / "footprints") {
          pathEndOrSingleSlash {
            get {
              onSuccess(FootprintService.getFootprints())  { resp =>
                complete(resp)
              }
            } ~
              post {
                entity(as[FootprintWithGeojsonCreate]) { footprint =>
                  onSuccess(FootprintService.insertFootprint(footprint)) {
                    case Success(footprint) => complete(footprint)
                    case Failure(e) => throw e
                  }
                }
              }
          }
        }
      }
    }
  }
}
