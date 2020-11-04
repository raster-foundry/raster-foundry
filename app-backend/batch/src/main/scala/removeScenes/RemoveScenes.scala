package com.rasterfoundry.batch.removescenes

import com.rasterfoundry.batch.Job
import com.rasterfoundry.database.util.RFTransactor

import cats.effect.IO
import cats.syntax.apply._
import cats.syntax.either._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class RemoveScenes(
    datasourceId: UUID,
    startDate: LocalDate,
    xa: Transactor[IO]
) {
  val endDate = startDate `with` TemporalAdjusters.firstDayOfNextMonth()
  val startTs = Timestamp.valueOf(startDate.atStartOfDay())
  val endTs = Timestamp.valueOf(endDate.atStartOfDay())

  def removeScenes: IO[Int] =
    fr"""
    DELETE FROM scenes
    USING scenes AS scn
    LEFT JOIN scenes_to_layers ON scn.id = scenes_to_layers.scene_id
    WHERE
      -- scene_id NULL means we didn't find a scene_id in the layers table
      scene_id IS NULL
      AND acquisition_date >= ${startTs}
      AND acquisition-date <= ${endTs}
      AND datasource = $datasourceId
    """.update.run.transact(xa)

}

object RemoveScenes extends Job {
  val name = "remove-scenes"
  implicit val unsafeLoggerIO = Slf4jLogger.getLogger[IO]
  val xa = RFTransactor.nonHikariTransactor(RFTransactor.TransactorConfig())
  def runJob(args: List[String]): IO[Unit] =
    args match {
      case datasourceIdString :: startDateString :: Nil =>
        (
          Either.catchNonFatal(UUID.fromString(datasourceIdString)),
          Either.catchNonFatal(LocalDate.parse(startDateString))
        ).tupled
          .traverse({
            case (datasourceId, startDate) =>
              val runner = new RemoveScenes(datasourceId, startDate, xa)
              runner.removeScenes
          }) flatMap {
          case Left(err) =>
            Logger[IO].error(err)(
              s"Failed to delete scenes for datasource $datasourceIdString"
            )
            IO { sys.exit(1) }
          case Right(nRemoved) =>
            Logger[IO]
              .info(
                s"Removed $nRemoved scenes for datasource $datasourceIdString"
              )
        }
      case _ =>
        IO.raiseError(
          new Exception(
            "Incorrect arguments -- I expected a UUID datasource ID and a YYYY-MM-DD date"
          )
        )
    }

}
