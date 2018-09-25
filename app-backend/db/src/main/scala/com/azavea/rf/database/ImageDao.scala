package com.azavea.rf.database

import java.sql.Timestamp
import java.util.UUID

import cats.implicits._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

object ImageDao extends Dao[Image] {

  val tableName = "images"

  val selectF: Fragment = sql"""
    SELECT
      id, created_at, modified_at, created_by, modified_by,
      owner, raw_data_bytes, visibility, filename, sourceuri, scene,
      image_metadata, resolution_meters, metadata_files FROM """ ++ tableF

  def create(image: Image, user: User): ConnectionIO[Image] = {
    val id = UUID.randomUUID
    val now = new Timestamp(new java.util.Date().getTime)
    val ownerId = util.Ownership.checkOwner(user, Some(image.owner))
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, created_by, modified_by,
        owner, raw_data_bytes, visibility, filename, sourceuri, scene,
        image_metadata, resolution_meters, metadata_files)
      VALUES
        (${image.id}, ${image.createdAt}, ${image.modifiedAt},
         ${user.id}, ${user.id}, ${ownerId}, ${image.rawDataBytes}, ${image.visibility},
         ${image.filename}, ${image.sourceUri}, ${image.scene},
         ${image.imageMetadata}, ${image.resolutionMeters}, ${image.metadataFiles})
    """).update.withUniqueGeneratedKeys[Image](
      "id",
      "created_at",
      "modified_at",
      "created_by",
      "modified_by",
      "owner",
      "raw_data_bytes",
      "visibility",
      "filename",
      "sourceuri",
      "scene",
      "image_metadata",
      "resolution_meters",
      "metadata_files"
    )
  }

  def insertImage(imageBanded: Image.Banded,
                  user: User): ConnectionIO[Option[Image.WithRelated]] = {
    val image = imageBanded.toImage(user)
    val bands: List[Band] = (imageBanded.bands map { band: Band.Create =>
      band.toBand(image.id)
    }).toList
    val imageWithRelated =
      Image.WithRelated.fromRecords(bands.map((image, _))).headOption
    val transaction = for {
      _ <- this.create(image, user)
      _ <- BandDao.createMany(bands)
    } yield imageWithRelated
    transaction
  }

  def insertManyImages(images: List[Image]): ConnectionIO[Int] = {
    val insertSql = s"""INSERT INTO ${tableName}
        (id, created_at, modified_at, created_by, modified_by,
        owner, raw_data_bytes, visibility, filename, sourceuri, scene,
        image_metadata, resolution_meters, metadata_files)
      VALUES
        (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    Update[Image](insertSql).updateMany(images)
  }

  //update images
  def updateImage(image: Image, id: UUID, user: User): ConnectionIO[Int] = {
    val now = new Timestamp(new java.util.Date().getTime)
    val updateQuery: Fragment =
      fr"UPDATE" ++ this.tableF ++ fr"SET" ++
        fr"""
          modified_at = ${now},
          modified_by = ${user.id},
          raw_data_bytes = ${image.rawDataBytes},
          visibility = ${image.visibility},
          filename = ${image.filename},
          sourceuri = ${image.sourceUri},
          scene = ${image.scene},
          image_metadata = ${image.imageMetadata},
          resolution_meters = ${image.resolutionMeters},
          metadata_files = ${image.metadataFiles}
      """ ++ Fragments.whereAndOpt(fr"id = ${id}".some)
    updateQuery.update.run
  }

  def deleteImage(id: UUID): ConnectionIO[Int] = {
    this.query.filter(id).delete
  }

  def getImage(id: UUID): ConnectionIO[Option[Image]] = {
    this.query.filter(id).selectOption
  }

  def unsafeGetImage(id: UUID): ConnectionIO[Image] = {
    this.query.filter(id).select
  }
}
