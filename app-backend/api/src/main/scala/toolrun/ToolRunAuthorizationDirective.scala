package com.rasterfoundry.api.toolrun

import com.rasterfoundry.akkautil.Authentication
import com.rasterfoundry.database.MapTokenDao

import akka.http.scaladsl.server._
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie._
import doobie.implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import java.util.UUID

trait ToolRunAuthorizationDirective extends Authentication with Directives {
  implicit val xa: Transactor[IO]

  def toolRunAuthProjectFromMapTokenO(mapTokenO: Option[UUID],
                                      projectId: UUID): Directive0 = {
    authorizeAsync {
      mapTokenO match {
        case Some(mapToken) =>
          MapTokenDao
            .checkProject(projectId)(mapToken)
            .transact(xa)
            .map({
              case Some(_) => true
              case _       => false
            })
            .unsafeToFuture
        case _ => false.pure[Future]
      }
    }
  }
}
