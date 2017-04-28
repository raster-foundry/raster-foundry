package com.azavea.rf.api.organization

import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Organizations
import com.azavea.rf.datamodel._

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import java.util.UUID


/**
  * Routes for Organizations
  */
trait OrganizationRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

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
      if (user.isInRootOrganization) {
        complete(Organizations.listOrganizations(page))
      } else {
        complete(Organizations.listFilteredOrganizations(List(user.organizationId), page))
      }
    }
  }

  def createOrganization: Route = authenticateRootMember { root =>
    entity(as[Organization.Create]) { newOrg =>
      onSuccess(Organizations.createOrganization(newOrg)) { org =>
        complete(StatusCodes.Created, org)
      }
    }
  }

  def getOrganization(orgId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      if (user.isInRootOrSameOrganizationAs(new { val organizationId = orgId })) {
        complete(Organizations.getOrganization(orgId))
      } else {
        complete(StatusCodes.NotFound)
      }
    }
  }

  def updateOrganization(orgId: UUID): Route = authenticateRootMember { root =>
    entity(as[Organization]) { updatedOrg =>
      onSuccess(Organizations.updateOrganization(updatedOrg, orgId)) {
        completeSingleOrNotFound
      }
    }
  }

}
