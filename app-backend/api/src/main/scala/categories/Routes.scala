package com.azavea.rf.api.category

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import cats.effect.IO

import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

import scala.util.{Success, Failure}

trait CategoryRoutes extends Authentication
    with PaginationDirectives
    with CategoryQueryParametersDirective
    with UserErrorHandler {
  val xa: Transactor[IO]

  // Not implementing an update function, since it's an emergency operation and should probably be done
  // in the database directly to avoid orphaning categorized analyses. Eventually, we should remove the ability
  // to add/remove categories using the API, as right now any user can do so. We will probably want to add/remove
  // categories manually in the database or through migrations
  val categoryRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listCategories } ~
      post { createCategory }
    } ~
    pathPrefix(Segment) { categorySlug =>
      pathEndOrSingleSlash {
        get { getCategory(categorySlug) } ~
        delete { deleteCategory(categorySlug) }
      }
    }
  }

  def listCategories: Route = authenticate { user =>
    (withPagination & categoryQueryParameters) { (page, combinedParams) =>
      complete {
        CategoryDao.query.filter(combinedParams).page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createCategory: Route =
    handleExceptions(userExceptionHandler) {
      authenticate { user =>
        entity(as[Category.Create]) { newCategory =>
          onSuccess(
            CategoryDao.insertCategory(
              newCategory.toCategory(user.id), user
            ).transact(xa).unsafeToFuture()
          ) { category =>
            complete((StatusCodes.Created, category))
          }
        }
    }
  }

  def getCategory(categorySlug: String): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        CategoryDao.query.filter(fr"slug_label = $categorySlug").selectOption.transact(xa).unsafeToFuture
      }
    }
  }

  def deleteCategory(categorySlug: String): Route = authenticate { user =>
    onSuccess(
      CategoryDao.deleteCategory(categorySlug, user)
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
