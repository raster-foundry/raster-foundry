package com.azavea.rf.api.organization

import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.OrganizationDao
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import java.util.UUID

import cats.effect.IO
import doobie.util.transactor.Transactor

import doobie.util.transactor.Transactor
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._


/**
  * Routes for Organizations
  */
trait OrganizationRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  implicit def xa: Transactor[IO]

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
        complete(OrganizationDao.query.page(page).transact(xa).unsafeToFuture)
      } else {
        complete(OrganizationDao.query.filter(List(user.organizationId)).page(page).transact(xa).unsafeToFuture())
      }
    }
  }

  def createOrganization: Route = authenticateRootMember { root =>
    entity(as[Organization.Create]) { newOrg =>
      onSuccess(OrganizationDao.createOrganization(newOrg).transact(xa).unsafeToFuture()) { org =>
        complete(StatusCodes.Created, org)
      }
    }
  }

  def getOrganization(orgId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      if (user.isInRootOrSameOrganizationAs(new { val organizationId = orgId })) {
        complete(OrganizationDao.query.selectOption(orgId).transact(xa).unsafeToFuture())
      } else {
        complete(StatusCodes.NotFound)
      }
    }
  }

  def updateOrganization(orgId: UUID): Route = authenticateRootMember { root =>
    entity(as[Organization]) { updatedOrg =>
      onSuccess(OrganizationDao.updateOrganization(updatedOrg, orgId).transact(xa).unsafeToFuture()) {
        completeSingleOrNotFound
      }
    }
  }

}
