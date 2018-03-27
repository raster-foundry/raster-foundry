package com.azavea.rf.api.template

import com.azavea.rf.common._
import com.azavea.rf.common.ast._
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.codec._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.maml.serve._
import io.circe._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import kamon.akka.http.KamonTraceDirectives
import java.util.UUID
import scala.util.{Failure, Success}

import cats.effect.IO
import com.azavea.rf.database._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._


trait TemplateRoutes extends Authentication
    with TemplateQueryParametersDirective
    with PaginationDirectives
    with CommonHandlers
    with KamonTraceDirectives
    with InterpreterExceptionHandling
    with UserErrorHandler {

  val xa: Transactor[IO]

  val templateRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        traceName("templates-list") {
          listTemplates
        }
      } ~
      post {
        traceName("templates-create") {
          createTemplate
        }
      }
    } ~
    pathPrefix(JavaUUID) { templateId =>
      pathEndOrSingleSlash {
        get {
          traceName("templates-detail") {
            getTemplate(templateId)
          }
        } ~
        put {
          traceName("templates-update") {
            updateTemplate(templateId)
          }
        } ~
        delete {
          traceName("templates-delete") {
            deleteTemplate(templateId) }
        }
      } ~
      pathPrefix("publish") {
        pathEndOrSingleSlash {
          post {
            traceName("templates-publish") {
              publishTemplate(templateId)
            }
          }
        }
      } ~
      pathPrefix("versions") {
        get {
          traceName("template-versions") {
            getTemplateVersions(templateId)
          }
        } ~
        pathPrefix(LongNumber) { versionId =>
          pathEndOrSingleSlash {
            get {
              traceName("templates-version-detail") {
                getTemplateVersion(templateId, versionId)
              }
            }
          }
        }
      }
    }
  }

  def listTemplates: Route = authenticate { user =>
    (withPagination & templateQueryParameters) { (page, combinedTemplateParams) =>
      complete {
        TemplateWithRelatedDao
          .listTemplates(page, combinedTemplateParams, user)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def createTemplate: Route = authenticate { user =>
    entity(as[Template.Create]) { newTemplate =>
      authorize(user.isInRootOrSameOrganizationAs(newTemplate)) {
        onComplete(TemplateDao.insert(newTemplate, user).transact(xa).unsafeToFuture) {
          case Success(template: Template) => complete((StatusCodes.Created, template))
          case Failure(error) => complete(StatusCodes.ClientError(400)("Error creating template", error.getMessage()))
        }
      }
    }
  }

  def getTemplate(templateId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(
        TemplateWithRelatedDao
          .getById(templateId, user)
          .transact(xa)
          .unsafeToFuture
      )
    }
  }

  def updateTemplate(templateId: UUID): Route = authenticate { user =>
    entity(as[Template]) { updatedTemplate =>
      authorize(user.isInRootOrSameOrganizationAs(updatedTemplate)) {
        onComplete(TemplateDao.update(updatedTemplate, templateId, user).transact(xa).unsafeToFuture) {
          case Success(updated: Int) => completeSingleOrNotFound(updated)
          case Failure(_) => complete(StatusCodes.NotFound)
        }
      }
    }
  }

  def deleteTemplate(templateId: UUID): Route = authenticate { user =>
    onComplete(
      TemplateDao.query
        .filter(fr"id = ${templateId}")
        .ownerFilter(user)
        .delete
        .transact(xa)
        .unsafeToFuture
    ) {
      case Success(deleteCount: Int) => completeSingleOrNotFound(deleteCount)
      case Failure(_) => complete(StatusCodes.InternalServerError)
    }
  }

  def publishTemplate(templateId: UUID): Route = authenticate { user =>
    entity(as[TemplateVersion.CreateWithRelated]) { tv =>
      onComplete(TemplateDao.publish(templateId, user, tv).transact(xa).unsafeToFuture) {
        case Success(Some(templateVersion)) => complete((StatusCodes.Created, templateVersion))
        case Success(None) => complete((StatusCodes.NotFound, "Template not found"))
        case Failure(error) => complete(
          StatusCodes.ClientError(400)("Error publishing to template", error.getMessage())
        )
      }
    }
  }

  def getTemplateVersions(templateId: UUID): Route = authenticate { user =>
    onComplete(
      TemplateDao
        .getById(templateId, user)
        .flatMap( template =>
          TemplateVersionDao.query.filter(fr"template_id = ${templateId}").list
        )
        .transact(xa).unsafeToFuture
    ) {
      case Success(templateVersions) => complete(templateVersions)
      case Failure(_) => complete(StatusCodes.NotFound)
    }
  }

  def getTemplateVersion(templateId: UUID, versionId: Long): Route = authenticate { user =>
    onComplete(
      TemplateDao
        .getById(templateId, user)
        .flatMap(TemplateVersionDao.getById(_, versionId, user))
        .transact(xa).unsafeToFuture()
    ) {
      case Success(Some(templateVersion)) => complete(templateVersion)
      case Success(None) => complete(StatusCodes.NotFound)
      case Failure(e) => complete((StatusCodes.InternalServerError, e.getMessage))
    }
  }
}
