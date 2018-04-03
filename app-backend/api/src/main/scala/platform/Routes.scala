package com.azavea.rf.api.platform

import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.PlatformDao
import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import io.circe._
import kamon.akka.http.KamonTraceDirectives

import scala.util.{Failure, Success}

import java.util.UUID

trait PlatformRoutes extends Authentication
  with PaginationDirectives
  with CommonHandlers
  with KamonTraceDirectives
  with UserErrorHandler {

  val xa: Transactor[IO]

  val platformRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        traceName("platforms-list") {
          listPlatforms
        }
      } ~
      post {
        traceName("platforms-create") {
          createPlatform
        }
      }
    } ~
    pathPrefix(JavaUUID) { platformId =>
      pathEndOrSingleSlash {
        get {
          traceName("platforms-get") {
            getPlatform(platformId)
          }
        } ~
        put {
          traceName("platforms-update") {
            updatePlatform(platformId)
          }
        } ~
        delete {
          traceName("platforms-delete") {
            deletePlatform(platformId)
          }
        }
      }
    }
  }

  // @TODO: most platform API interactions should be highly restricted -- only 'super-users' should
  // be able to do list, create, update, delete. Non-super users can only get a platform if they belong to it.
  def listPlatforms: Route = authenticate { user =>
    (withPagination) { page =>
      complete {
        PlatformDao.query.page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createPlatform: Route = authenticate { user =>
    entity(as[Platform.Create]) { platformToCreate =>
      completeOrRecoverWith(PlatformDao.create(platformToCreate.toPlatform).transact(xa).unsafeToFuture) { err =>
        failWith(err)
      }
    }
  }

  def getPlatform(platformId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        PlatformDao.query.filter(platformId).selectOption.transact(xa).unsafeToFuture
      }
    }
  }

  def updatePlatform(platformId: UUID): Route = authenticate { user =>
    entity(as[Platform]) { platformToUpdate =>
      onComplete(PlatformDao.update(platformToUpdate, platformId, user).transact(xa).unsafeToFuture) {
        case Success(count) => completeSingleOrNotFound(count)
        case Failure(err) => failWith(err)
      }
    }
  }

  def deletePlatform(platformId: UUID): Route = authenticate { user =>
    onComplete(PlatformDao.query.filter(platformId).delete.transact(xa).unsafeToFuture) {
      case Success(count) => completeSingleOrNotFound(count)
      case Failure(err) => failWith(err)
    }
  }
}
