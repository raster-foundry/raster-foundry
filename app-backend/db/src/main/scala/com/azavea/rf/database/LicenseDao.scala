package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.util._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._

import cats._, cats.data._, cats.effect.IO, cats.implicits._
import geotrellis.slick.Projected
import geotrellis.vector.Geometry
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
