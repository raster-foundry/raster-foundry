package com.rasterfoundry.api.campaign

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server._
import cats.effect.IO
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
        CampaignDao
          .isActiveCampaign(campaignId)
          .transact(xa)
          .unsafeToFuture
      } {
        (withPagination & annotationProjectQueryParameters) {
          (page, annotationProjectQP) =>
            complete {
              AnnotationProjectDao
                .listProjects(
                  page,
                  annotationProjectQP.copy(campaignId = Some(campaignId)),
                  user
                )
                .transact(xa)
                .unsafeToFuture
            }
        }
      }

    }
  }
}
