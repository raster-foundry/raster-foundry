package com.azavea.rf.organization

import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables._
import com.azavea.rf.datamodel._
import com.azavea.rf.utils.UserErrorHandler
import com.azavea.rf.datamodel.Organization

/**
  * Routes for Organizations
  */
trait OrganizationRoutes extends Authentication with PaginationDirectives with UserErrorHandler {

  implicit def database: Database
  implicit val ec: ExecutionContext

  def organizationRoutes: Route = {
    handleExceptions(userExceptionHandler) {
      authenticate { user =>
        pathPrefix("api" / "organizations") {
          pathEndOrSingleSlash {
            withPagination { page =>
              get {
                onSuccess(Organizations.getOrganizationList(page)) { resp =>
                  complete(resp)
                }
              } ~
              post {
                entity(as[OrganizationCreate]) { orgCreate =>
                  onSuccess(Organizations.createOrganization(orgCreate)) {
                    case Success(newOrg) => complete(newOrg)
                    case Failure(e) => throw e
                  }
                }
              }
            }
          } ~
          pathPrefix(JavaUUID) { orgId =>
            pathEndOrSingleSlash {
              get {
                onSuccess(Organizations.getOrganization(orgId)) {
                  case Some(org) => complete(org)
                  case _ => complete(StatusCodes.NotFound)
                }
              } ~
              put {
                entity(as[Organization]) { orgUpdate =>
                  onSuccess(Organizations.updateOrganization(orgUpdate, orgId)) {
                    case Success(res) => {
                      res match {
                        case 1 => complete(StatusCodes.NoContent)
                        case count: Int => throw new Exception(
                          s"Error updating organization: update result expected to be: 1, was $count"
                        )
                      }
                    }
                    case Failure(e) => throw e
                  }
                }
              }
            } ~
            pathPrefix("users") {
              pathEndOrSingleSlash {
                withPagination { page =>
                  get {
                    onSuccess(Organizations.getOrganizationUsers(page, orgId)) { resp =>
                      complete(resp)
                    }
                  }
                } ~
                post {
                  entity(as[UserWithRoleCreate]) { userWithRole =>
                    onSuccess(Organizations.addUserToOrganization(userWithRole, orgId)) {
                      case Success(userRole) => complete(userRole)
                      case Failure(e) => throw e
                    }
                  }
                }
              } ~
              pathPrefix(Segment) { userId =>
                get {
                  onSuccess(Organizations.getUserOrgRole(userId, orgId)) {
                    case Some(userRole) => complete(userRole)
                    case _ => complete(StatusCodes.NotFound)
                  }
                } ~
                put {
                  entity(as[UserWithRole]) { userWithRole =>
                    onSuccess(
                      Organizations.updateUserOrgRole(userWithRole, orgId, userId)
                    ) {
                      case Success(res) => {
                        res match {
                          case 1 => complete(StatusCodes.NoContent)
                          case _ => complete(StatusCodes.InternalServerError)
                        }
                      }
                      case Failure(e) => throw e
                    }
                  }
                } ~
                delete {
                  onSuccess(Organizations.deleteUserOrgRole(userId, orgId)) {
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
  }
}
