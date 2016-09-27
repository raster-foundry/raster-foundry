package com.azavea.rf.footprint


import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.utils.{Database, UserErrorHandler}


trait FootprintRoutes extends Authentication
    with PaginationDirectives
    with FootprintQueryParameterDirective
    with UserErrorHandler {

  implicit def database: Database
  implicit val ec: ExecutionContext

  def footprintRoutes:Route = {
    handleExceptions(userExceptionHandler) {
      pathPrefix("api" / "footprints") {
        pathEndOrSingleSlash {
          authenticateAndAllowAnonymous { user =>
            withPagination { page =>
              get {
                footprintQueryParameters { footParams =>
                  onSuccess(FootprintService.listFootprints(page, footParams))  { resp =>
                    complete(resp)
                  }
                }
              }
            }
          } ~
          authenticate { user =>
            post {
              entity(as[FootprintWithGeojsonCreate]) { footprintCreate =>
                onSuccess(FootprintService.insertFootprint(footprintCreate)) {
                  case Success(footprint) => complete(footprint)
                  case Failure(e) => throw e
                }
              }
            }
          }
        } ~
        pathPrefix(JavaUUID) { fpId =>
          authenticateAndAllowAnonymous { user =>
            get {
              onSuccess(FootprintService.getFootprint(fpId)) {
                case Some(footprint) => complete(footprint)
                case _ => complete(StatusCodes.NotFound)
              }
            }
          } ~
          authenticate { user =>
            put {
              entity(as[FootprintWithGeojson]) { fpUpdate =>
                onSuccess(FootprintService.updateFootprint(fpUpdate, fpId)) {
                  case Success(res) => {
                    res match {
                      case 1 => complete(StatusCodes.NoContent)
                      case count: Int => throw new Exception(
                        s"Error updating footprint: update result expected to be: 1, was $count"
                      )
                    }
                  }
                  case Failure(e) => throw e
                }
              }
            } ~
            delete {
              onSuccess(FootprintService.deleteFootprint(fpId)) {
                case 1 => complete(StatusCodes.NoContent)
                case 0 => complete(StatusCodes.NotFound)
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        }
      }
    }
  }
}
