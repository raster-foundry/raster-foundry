package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import java.sql.Timestamp
import java.util.{Date, UUID}


object ImageDao extends Dao[Image] {

  val tableName = "images"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, organization_id, created_by, modified_by,
      owner, raw_data_bytes, visibility, filename, sourceuri, scene,
      image_metadata, resolution_meters, metadata_files FROM """ ++ tableF 
  def create(
    image: Image,
    user: User
  ): ConnectionIO[Image] = {
    val id = UUID.randomUUID
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, Some(image.owner))
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, organization_id, created_by, modified_by,
        owner, raw_data_bytes, visibility, filename, sourceuri, scene,
        image_metadata, resolution_meters, metadata_files)
      VALUES
        (${image.id}, ${image.createdAt}, ${image.modifiedAt}, ${image.organizationId},
         ${user.id}, ${user.id}, ${ownerId}, ${image.rawDataBytes}, ${image.visibility},
         ${image.filename}, ${image.sourceUri}, ${image.scene},
         ${image.imageMetadata}, ${image.resolutionMeters}, ${image.metadataFiles})
    """).update.withUniqueGeneratedKeys[Image](
        "id", "created_at", "modified_at", "organization_id", "created_by", "modified_by",
        "owner", "raw_data_bytes", "visibility", "filename", "sourceuri", "scene",
        "image_metadata", "resolution_meters", "metadata_files"
    )
  }

  def insertImage(imageBanded: Image.Banded, user: User): ConnectionIO[Option[Image.WithRelated]] = {
    val image = imageBanded.toImage(user)
    val bands: Seq[Band] = imageBanded.bands map { _.toBand(image.id) }
    val imageWithRelated = Image.WithRelated.fromRecords(bands.map((image, _))).headOption
    val transaction = for {
      _ <- this.create(image, user)
      _ <- BandDao.createMany(bands)
    } yield imageWithRelated
    transaction
  }

  def insertManyImages(images: List[Image]): ConnectionIO[Int] = {
    val insertSql = s"""INSERT INTO ${tableName}
        (id, created_at, modified_at, organization_id, created_by, modified_by,
        owner, raw_data_bytes, visibility, filename, sourceuri, scene,
        image_metadata, resolution_meters, metadata_files)
      VALUES
        (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    Update[Image](insertSql).updateMany(images)
  }

  //update images
  def updateImage(image: Image, id: UUID, user: User): ConnectionIO[Int] = {
    val now = new Timestamp((new java.util.Date()).getTime())
    val updateQuery: Fragment =
      fr"UPDATE" ++ this.tableF ++ fr"SET" ++
        fr"""
          modified_at = ${now},
          modified_by = ${user.id},
          rawDataBytes = ${image.rawDataBytes},
          visibility = ${image.visibility},
          filename = ${image.filename},
          sourceuri = ${image.sourceUri},
          scene = ${image.scene},
          image_metadata = ${image.imageMetadata},
          resolution_meters = ${image.resolutionMeters},
          metadata_files = ${image.metadataFiles}
          where id = ${id} AND owner = ${user.id}
      """
    updateQuery.update.run
  }

  // delete images
  def deleteImage(id: UUID, user: User): ConnectionIO[Int] = {
    this.query.filter(fr"owner = ${user.id}").filter(fr"id = ${id}").delete
  }

  // get image
  def getImage(id: UUID, user: User): ConnectionIO[Option[Image]] = {
    this.query.filter(fr"owner = ${user.id} OR owner = 'default'").selectOption
  }
}

