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
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

import scala.util.{Failure, Success}

trait OrganizationRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  val xa: Transactor[IO]

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
        OrganizationDao.query.page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createOrganization: Route = authenticateRootMember { root =>
    entity(as[Organization.Create]) { orgToCreate =>
      completeOrFail {
        OrganizationDao.create(orgToCreate.toOrganization).transact(xa).unsafeToFuture()
      }
    }
  }

  def getOrganization(orgId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        OrganizationDao.query.filter(orgId).selectOption.transact(xa).unsafeToFuture()
      }
    }
  }

  def updateOrganization(orgId: UUID): Route = authenticateRootMember { root =>
    entity(as[Organization]) { orgToUpdate =>
      completeWithOneOrFail {
        OrganizationDao.update(orgToUpdate, orgId).transact(xa).unsafeToFuture()
      }
    }
  }

  // @TODO: There is no delete functionality as we most likely will want to instead deactivate platforms
}
