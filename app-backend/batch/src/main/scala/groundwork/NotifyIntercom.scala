package com.rasterfoundry.batch.groundwork

import com.rasterfoundry.batch.Job
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.notification.intercom.IntercomConversation
import com.rasterfoundry.notification.intercom.Model._
import com.rasterfoundry.notification.intercom._

import cats.effect.{Async, IO}
import doobie.hikari.HikariTransactor
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

object NotifyIntercomProgram extends Job {
  val name = "notify-intercom"

  val getBackend = for {
    backendRef <- Async.memoize {
      AsyncHttpClientCatsBackend[IO]()
    }
    backend <- backendRef
  } yield backend

  def runJob(args: List[String]): IO[Unit] = {
    RFTransactor.xaResource.use(transactor => {
      implicit val xa: HikariTransactor[IO] = transactor

      val dbIO = new DbIO(xa);

      args match {
        case externalId +: msg +: Nil =>
          for {
            backend <- getBackend
            _ <- {
              IntercomConversation.notifyIO(
                externalId,
                Message(msg),
                dbIO.groundworkConfig,
                new LiveIntercomNotifier[IO](backend),
                dbIO.getConversation,
                dbIO.insertConversation
              )
            }
          } yield ()
        case _ =>
          IO.raiseError(
            new Exception(
              s"Arguments should match pattern `USER_ID MESSAGE`. Got $args"
            )
          )
      }

    })
  }
}
