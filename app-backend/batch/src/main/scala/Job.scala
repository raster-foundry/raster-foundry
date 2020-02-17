package com.rasterfoundry.batch

import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier

import cats.effect._

import java.util.concurrent.ForkJoinPool

trait Job extends IOApp with Config with RollbarNotifier {
  val name: String

  private def acquireThreadPool = IO { new ForkJoinPool(16) }
  private def releaseThreadPool(pool: ForkJoinPool) =
    IO { logger.info("Shutting down threadpool") } map { _ =>
      pool.shutdown()
    } map { _ =>
      logger.info("Thread pool shutdown completed")
    }
  val threadPoolResource = Resource.make(acquireThreadPool)(releaseThreadPool)

  /** Run function should be defined for all Jobs */
  def runJob(args: List[String]): IO[Unit]

  def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- runJob(args)
    } yield ExitCode.Success
}
