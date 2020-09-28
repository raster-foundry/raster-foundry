package com.rasterfoundry.api.campaign

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server._
import cats.effect.IO
import cats.syntax.apply._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import java.util.UUID

trait CampaignProjectRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with PaginationDirectives
    with QueryParametersCommon {
  val xa: Transactor[IO]

  def listCampaignProjects(campaignId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationProjects, Action.Read, None),
      user
    ) {
      authorizeAsync {
        (
          CampaignDao
            .isActiveCampaign(campaignId),
          CampaignDao.authorized(
            user,
            ObjectType.Campaign,
            campaignId,
            ActionType.View
          )
        ).tupled.transact(xa).unsafeToFuture map {
          case (true, AuthSuccess(_)) => true
          case _                      => false
        }
      } {
        (withPagination & annotationProjectQueryParameters) {
          (page, annotationProjectQP) =>
            complete {
              AnnotationProjectDao.query
                .filter(annotationProjectQP.copy(campaignId = Some(campaignId)))
                .page(page)
                .transact(xa)
                .unsafeToFuture
            }
        }
      }

    }
  }

  def getCampaignProject(campaignId: UUID, annotationProjectId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Read, None),
        user
      ) {
        authorizeAsync {
          (
            CampaignDao.isActiveCampaign(campaignId),
            CampaignDao
              .authorized(
                user,
                ObjectType.Campaign,
                campaignId,
                ActionType.View
              ),
            AnnotationProjectDao.authorized(
              user,
              ObjectType.AnnotationProject,
              annotationProjectId,
              ActionType.View
            )
          ).tupled.transact(xa).unsafeToFuture map {
            case (true, campaignResult, annotationProjectResult) =>
              campaignResult.toBoolean || annotationProjectResult.toBoolean
            case _ => false
          }
        } {
          complete {
            AnnotationProjectDao
              .listByCampaignQB(campaignId)
              .filter(annotationProjectId)
              .selectOption
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
}
