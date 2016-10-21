package com.azavea.rf.database.tables

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import slick.model.ForeignKeyAction
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging

class Bands(_tableTag: Tag) extends Table[Band](_tableTag, "bands")
    with LazyLogging {
  def * = (id, imageId, name, number, wavelength) <> (Band.tupled, Band.unapply)

  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val imageId: Rep[UUID] = column[UUID]("image_id")
  val name: Rep[String] = column[String]("name")
  val number: Rep[Int] = column[Int]("number")
  val wavelength: Rep[List[Int]] = column[List[Int]]("wavelength")

  lazy val imagesFk = foreignKey("bands_images_id_fkey", imageId, Images)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
}

object Bands extends TableQuery(tag => new Bands(tag)) with LazyLogging {
  type TableQuery = Query[Bands, Bands#TableElementType, Seq]
}
