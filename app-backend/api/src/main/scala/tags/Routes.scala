package com.azavea.rf.api.tag

import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.TagDao
import com.azavea.rf.datamodel._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._

import scala.util.{Failure, Success}
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database.filter.Filterables._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._



trait TagRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {
  val xa: Transactor[IO]

  val tagRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listTags } ~
      post { createTag }
    } ~
    pathPrefix(JavaUUID) { tagId =>
      pathEndOrSingleSlash {
        get { getTag(tagId) } ~
        put { updateTag(tagId) } ~
        delete { deleteTag(tagId) }
      }
    }
  }

  def listTags: Route = authenticate { user =>
    (withPagination) { (page) =>
      complete {
        TagDao.query.ownerFilter(user).page(page).transact(xa).unsafeToFuture()
      }
    }
  }

  def createTag: Route = authenticate { user =>
    entity(as[Tag.Create]) { newTag =>
      authorize(user.isInRootOrSameOrganizationAs(newTag)) {
        onComplete(TagDao.insert(newTag, user).transact(xa).unsafeToFuture()) {
          case Success(tag) => complete(StatusCodes.Created, tag)
          case Failure(e) => complete((StatusCodes.InternalServerError, e.getMessage))
        }
      }
    }
  }

  def getTag(tagId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(TagDao.query.ownerFilter(user).filter(fr"id = ${tagId}").select.transact(xa).unsafeToFuture)
    }
  }

  def updateTag(tagId: UUID): Route = authenticate { user =>
    entity(as[Tag]) { updatedTag =>
      authorize(user.isInRootOrSameOrganizationAs(updatedTag)) {
        onComplete(TagDao.update(updatedTag, tagId, user).transact(xa).unsafeToFuture()) {
          case Success(count) => completeSingleOrNotFound(count)
          case Failure(e) => complete((StatusCodes.InternalServerError, e.getMessage))
        }
      }
    }
  }

  def deleteTag(tagId: UUID): Route = authenticate { user =>
    onComplete(TagDao.query.ownerFilter(user).filter(fr"id = ${tagId}").delete.transact(xa).unsafeToFuture()) {
      case Success(count) => completeSingleOrNotFound(count)
      case Failure(e) => complete((StatusCodes.InternalServerError, e.getMessage))
    }
  }

}
