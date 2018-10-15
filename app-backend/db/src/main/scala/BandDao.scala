package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import doobie.Fragments
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID

object BandDao extends Dao[Band] {

  val tableName = "bands"

  val selectF =
    sql"""
      SELECT
        id, image_id, name, number, wavelength
      FROM
    """ ++ tableF

  def createMany(bands: List[Band]): ConnectionIO[Int] = {
    val bandFragments: List[Fragment] = bands map { (band: Band) =>
      fr"(${band.id}, ${band.image}, ${band.name}, ${band.number}, ${band.wavelength})"
    }
    val insertFragment = fr"INSERT INTO" ++ tableF ++ fr"(id, image_id, name, number, wavelength) VALUES" ++ {
      bandFragments.toNel match {
        case Some(fragments) =>
          fragments.intercalate(fr",")
        case None =>
          throw new IllegalArgumentException(
            "Can't insert bands from an empty list")
      }
    }
    insertFragment.update.run
  }
}
