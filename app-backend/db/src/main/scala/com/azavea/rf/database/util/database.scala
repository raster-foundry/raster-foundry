package com.azavea.rf.database.util

import com.typesafe.config.ConfigFactory
import doobie.hikari.HikariTransactor
import cats.effect.IO
import doobie.hikari.implicits._
import scala.concurrent.duration._

trait Config {
//  private val config = ConfigFactory.load()
//  private val databaseConfig = config.getConfig("db")
  val jdbcNoDBUrl = "jdbc:postgresql://database.service.rasterfoundry.internal/"
  val jdbcDBName = "rasterfoundry"
  val jdbcUrl = jdbcNoDBUrl + jdbcDBName
  val dbUser = "rasterfoundry"
  val dbPassword = "rasterfoundry"
}

object RFTransactor extends Config {
  implicit lazy val xa = HikariTransactor.newHikariTransactor[IO](
    "org.postgresql.Driver",
    jdbcUrl,
    dbUser,
    dbPassword
  ).unsafeRunTimed(10 seconds) match {
    case Some(x) => x
    case _ => throw new Exception("ERRRR")
  }
}
