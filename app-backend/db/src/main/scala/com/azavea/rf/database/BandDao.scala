package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.Fragments
import cats._, cats.data._, cats.effect.IO, cats.implicits._

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

  def create(band: Band): ConnectionIO[Band] = {
    val id = UUID.randomUUID
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, image_id, name, number, wavelength)
      VALUES
        (${band.id}, ${band.image}, ${band.name}, ${band.number}, ${band.wavelength})
    """).update.withUniqueGeneratedKeys[Band](
      "id", "image_id", "name", "number", "wavelength"
    )
  }

  def createMany(bands: Seq[Band]): ConnectionIO[Int] = {
    (fr"INSERT INTO" ++ tableF ++ fr"(id, image_id, name, number, wavelength) VALUES" ++
       bands.foldLeft(fr"")(
         (query: Fragment, band: Band) => {
           query.toString().isEmpty() match {
             case true =>
               fr"(${band.id}, ${band.image}, ${band.name}, ${band.number}, ${band.wavelength})"
             case false =>
               query ++ fr", (${band.id}, ${band.image}, ${band.name}, ${band.number}, ${band.wavelength})"
           }
         })
    ).update.run
  }
}

