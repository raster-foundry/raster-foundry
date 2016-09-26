package com.azavea.rf.organization

import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.utils.{Database, UserErrorHandler}
import com.azavea.rf.datamodel.latest.schema.tables.OrganizationsRow

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
                onSuccess(OrganizationService.getOrganizationList(page)) { resp =>
                  complete(resp)
                }
              } ~
              post {
                entity(as[OrganizationsRowCreate]) { orgCreate =>
                  onSuccess(OrganizationService.createOrganization(orgCreate)) {
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
                onSuccess(OrganizationService.getOrganization(orgId)) { 
                  case Some(org) => complete(org)
                  case _ => complete(StatusCodes.NotFound)
                }
              } ~
              put {
                entity(as[OrganizationsRow]) { orgUpdate =>
                  onSuccess(OrganizationService.updateOrganization(orgUpdate, orgId)) {
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
                    onSuccess(OrganizationService.getOrganizationUsers(page, orgId)) { resp =>
                      complete(resp)
                    }
                  }
                } ~
                post {
                  entity(as[UserWithRoleCreate]) { userWithRole =>
                    onSuccess(OrganizationService.addUserToOrganization(userWithRole, orgId)) {
                      case Success(userRole) => complete(userRole)
                      case Failure(e) => throw e
                    }
                  }
                }
              } ~
              pathPrefix(Segment) { userId =>
                get {
                  onSuccess(OrganizationService.getUserOrgRole(userId, orgId)) {
                    case Some(userRole) => complete(userRole)
                    case _ => complete(StatusCodes.NotFound)
                  }
                } ~
                put {
                  entity(as[UserWithRole]) { userWithRole =>
                    onSuccess(
                      OrganizationService.updateUserOrgRole(userWithRole, orgId, userId)
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
                  onSuccess(OrganizationService.deleteUserOrgRole(userId, orgId)) {
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
