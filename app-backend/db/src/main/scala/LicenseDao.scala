package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._

import cats._, cats.data._, cats.effect.IO, cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}

object LicenseDao extends Dao[License] {

  val tableName = "licenses"

  val selectF =
    fr"""
       SELECT
         short_name, name, url, osi_approved
       FROM
      """ ++ tableF
}
