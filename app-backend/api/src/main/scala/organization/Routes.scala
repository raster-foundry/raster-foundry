package com.azavea.rf.api.organization

import java.util.UUID

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Organizations
import com.azavea.rf.datamodel._


/**
  * Routes for Organizations
  */
trait OrganizationRoutes extends Authentication with PaginationDirectives with UserErrorHandler {

  implicit def database: Database

  val organizationRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listOrganizations } ~
      post { createOrganization }
    } ~
    pathPrefix(JavaUUID) { orgId =>
      pathEndOrSingleSlash {
        get { getOrganization(orgId) } ~
        put { updateOrganization(orgId) }
      } ~
      pathPrefix("users") {
        pathEndOrSingleSlash {
          get { listOrganizationUsers(orgId) } ~
          post { addUserToOrganization(orgId) }
        } ~
        pathPrefix(Segment) { userId =>
          get { getOrganizationUser(orgId, userId) } ~
          put { updateOrganizationUser(orgId, userId) } ~
          delete { deleteOrganizationUser(orgId, userId) }
        }
      }
    }
  }

  def listOrganizations: Route = authenticate { user =>
    withPagination { page =>
      complete {
        Organizations.listOrganizations(page)
      }
    }
  }

  def createOrganization: Route = authenticate { user =>
    entity(as[Organization.Create]) { newOrg =>
      onSuccess(Organizations.createOrganization(newOrg)) { org =>
        complete(StatusCodes.Created, org)
      }
    }
  }

  def getOrganization(orgId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        Organizations.getOrganization(orgId)
      }
    }
  }

  def updateOrganization(orgId: UUID): Route = authenticate { user =>
    entity(as[Organization]) { updatedOrg =>
      onSuccess(Organizations.updateOrganization(updatedOrg, orgId)) {
        case 1 => complete(StatusCodes.NoContent)
        case count => throw new IllegalStateException(
          s"Error updating organization: update result expected to be: 1, was $count"
        )
      }
    }
  }

  def listOrganizationUsers(orgId: UUID): Route = authenticate { user =>
    withPagination { page =>
      complete {
        Organizations.listOrganizationUsers(page, orgId)
      }
    }
  }

  def addUserToOrganization(orgId: UUID): Route = authenticate { user =>
    entity(as[User.WithRoleCreate]) { userWithRole =>
      complete { Organizations.addUserToOrganization(userWithRole, orgId) }
    }
  }

  def getOrganizationUser(orgId: UUID, userId: String): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        Organizations.getUserOrgRole(userId, orgId)
      }
    }
  }

  def updateOrganizationUser(orgId: UUID, userId: String): Route = authenticate { user =>
    entity(as[User.WithRole]) { userWithRole =>
      onSuccess(Organizations.updateUserOrgRole(userWithRole, orgId, userId)) {
        case 1 => complete(StatusCodes.NoContent)
        case 0 => complete(StatusCodes.NotFound)
        case count => throw new IllegalStateException(
          s"Error updating organization users: update result expected to be: 1, was $count"
        )
      }
    }
  }

  def deleteOrganizationUser(orgId: UUID, userId: String): Route = authenticate { user =>
    onSuccess(Organizations.deleteUserOrgRole(userId, orgId)) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting organization users: delete result expected to be: 1, was $count"
      )
    }
  }
}
