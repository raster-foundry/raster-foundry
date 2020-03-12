package com.rasterfoundry.database

import cats.effect.{IO, LiftIO}
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import doobie.implicits._

trait ConnectionIOLogger extends LazyLogging {

  def trace(s: String): ConnectionIO[Unit] = LiftIO[ConnectionIO].liftIO {
    IO { logger.trace(s) }
  }

  def debug(s: String): ConnectionIO[Unit] = LiftIO[ConnectionIO].liftIO {
    IO { logger.debug(s) }
  }

  def info(s: String): ConnectionIO[Unit] = LiftIO[ConnectionIO].liftIO {
    IO { logger.info(s) }
  }

  def warn(s: String): ConnectionIO[Unit] = LiftIO[ConnectionIO].liftIO {
    IO { logger.warn(s) }
  }

  def error(s: String): ConnectionIO[Unit] = LiftIO[ConnectionIO].liftIO {
    IO { logger.error(s) }
  }
}
