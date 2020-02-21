package com.rasterfoundry.database

import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

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
    bandFragments.toNel map { fragments =>
      (fr"INSERT INTO" ++ tableF ++ fr"(id, image_id, name, number, wavelength) VALUES" ++ fragments
        .intercalate(fr",")).update.run
    } getOrElse { 0.pure[ConnectionIO] }
  }
}
