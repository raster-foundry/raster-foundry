package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta

import doobie._, doobie.implicits._
import doobie.hikari._, doobie.hikari.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

trait DBTestConfig extends RFMeta {

  val xa: Transactor[IO] =
    Transactor.after.set(
      Transactor.fromDriverManager[IO](
        "org.postgresql.Driver",
        "jdbc:postgresql://database.service.rasterfoundry.internal/",
        "rasterfoundry",
        "rasterfoundry"
      ),
      HC.rollback
    )

  val transactor = xa

}

