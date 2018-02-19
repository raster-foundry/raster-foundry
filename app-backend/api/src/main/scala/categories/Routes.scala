package com.azavea.rf.api.toolcategory

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import doobie._
import doobie.implicits._
import cats.effect.IO

import scala.util.{Success, Failure}

trait ToolCategoryRoutes extends Authentication
    with PaginationDirectives
    with ToolCategoryQueryParametersDirective
    with UserErrorHandler {
  implicit def xa: Transactor[IO]

  // Not implementing an update function, since it's an emergency operation and should probably be done
  // in the database directly to avoid orphaning categorized tools. Eventually, we should remove the ability
  // to add/remove categories using the API, as right now any user can do so. We will probably want to add/remove
  // categories manually in the database or through migrations
  val toolCategoryRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listToolCategories } ~
      post { createToolCategory }
    } ~
    pathPrefix(Segment) { toolCategorySlug =>
      pathEndOrSingleSlash {
        get { getToolCategory(toolCategorySlug) } ~
        delete { deleteToolCategory(toolCategorySlug) }
      }
    }
  }

  def listToolCategories: Route = authenticate { user =>
    (withPagination & toolCategoryQueryParameters) { (page, combinedParams) =>
      complete {
        ToolCategoryDao.query.filter(combinedParams).page(page)
      }
    }
  }

  def createToolCategory: Route =
    handleExceptions(userExceptionHandler) {
      authenticate { user =>
        entity(as[ToolCategory.Create]) { newToolCategory =>
          onSuccess(
            ToolCategoryDao.insertToolCategory(
              newToolCategory.toToolCategory(user.id), user
            ).transact(xa).unsafeToFuture()
          ) { toolCategory =>
            complete((StatusCodes.Created, toolCategory))
          }
        }
    }
  }

  def getToolCategory(toolCategorySlug: String): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        ToolCategoryDao.query.filter(fr"slug_label = $toolCategorySlug").selectOption
      }
    }
  }

  def deleteToolCategory(toolCategorySlug: String): Route = authenticate { user =>
    onSuccess(
      ToolCategoryDao.deleteToolCategory(toolCategorySlug, user)
        .transact(xa).unsafeToFuture
    ) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting tag: delete result expected to be 1, was $count"
      )
    }
  }

}
