package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.{ProjectDao, MetricDao, ToolRunDao}

import cats.data._
import cats.effect._
import cats.implicits._
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._
import org.http4s._
import org.http4s.headers._
import org.http4s.dsl.io._

import java.time.Instant
import java.util.UUID

class MetricMiddleware[F[_]](xa: Transactor[F])(implicit Conc: Concurrent[F]) {

  def middleware(http: AuthedService[User, F]): AuthedService[User, F] =
    Kleisli { withMetrics(http) }

  def withMetrics(http: AuthedService[User, F])(
      authedReq: AuthedRequest[F, User]): OptionT[F, Response[F]] =
    authedReq match {
      case _ as user if !Config.metrics.enableMetrics => http(authedReq)
      case req @ GET -> Root / UUIDWrapper(projectId) / "layers" / UUIDWrapper(
            layerId) / IntVar(z) / IntVar(x) / IntVar(y) as user =>
        for {
          metricFib <- OptionT.liftF {
            Conc.start {
              (ProjectDao.getProjectById(projectId) flatMap { projectO =>
                (projectO map { project =>
                  {
                    val metric =
                      Metric(Instant.now,
                             ProjectLayerMosaicEvent(projectId,
                                                     layerId,
                                                     project.owner,
                                                     getReferer(req.req)),
                             user.id)
                    MetricDao.insert(metric)
                  }
                }).getOrElse(0.pure[ConnectionIO])
              }).transact(xa)
            }
          }
          resp <- http(req)
        } yield resp

      case req @ GET -> Root / UUIDWrapper(projectId) / "analyses" / UUIDWrapper(
            analysisId) / IntVar(z) / IntVar(x) / IntVar(y) :? NodeQueryParamMatcher(
            node) as user =>
        for {
          metricFib <- OptionT.liftF {
            Conc.start {
              analysisToMetricFib(analysisId,
                                  Some(projectId),
                                  node,
                                  user.id,
                                  getReferer(req.req))
            }
          }
          resp <- http(req)
        } yield resp

      case req @ GET -> Root / UUIDWrapper(analysisId) / IntVar(z) / IntVar(x) / IntVar(
            y) :? NodeQueryParamMatcher(node) as user
          if req.req.scriptName == "/tools" =>
        for {
          metricFib <- OptionT.liftF {
            Conc.start {
              analysisToMetricFib(analysisId,
                                  None,
                                  node,
                                  user.id,
                                  getReferer(req.req))
            }
          }
          resp <- http(req)
        } yield resp
      case GET -> _ as use => http(authedReq)
    }

  private def analysisToMetricFib(analysisId: UUID,
                                  projectId: Option[UUID],
                                  nodeId: Option[UUID],
                                  requester: String,
                                  referer: String) =
    (ToolRunDao.query
      .filter(analysisId)
      .selectOption flatMap { toolRunO =>
      toolRunO
        .map({ toolRun =>
          {
            val metric =
              Metric(
                Instant.now,
                AnalysisEvent(projectId orElse toolRun.projectId,
                              toolRun.projectLayerId,
                              toolRun.id,
                              nodeId,
                              toolRun.owner,
                              referer),
                requester
              )
            MetricDao.insert(metric)
          }
        })
        .getOrElse { 0.pure[ConnectionIO] }
    }).transact(xa)

  private def getReferer[F[_]](req: Request[F]): String =
    req.headers.get(Referer) map { _.value } getOrElse ""
}
