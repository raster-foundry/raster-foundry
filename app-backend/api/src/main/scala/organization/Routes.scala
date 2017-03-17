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

}
