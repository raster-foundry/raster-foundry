package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.data._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object ImageWithRelatedDao extends Dao[Image.WithRelated] {
  val tableName = "images"

  val selectF = ImageDao.selectF

  def bandsForImages(imageIds: NonEmptyList[UUID]): ConnectionIO[List[Band]] = {
    // When interpolating lists, doobie is interpolating List(List(...)) instead of the list
    BandDao.query.filter(Fragments.in(fr"image_id", imageIds)).list
  }

  def imagesAndBandsToImagesWithRelated(
      images: List[Image],
      bands: List[Band]): List[Image.WithRelated] = {
    val grouped = bands.groupBy(_.image)
    images map { im: Image =>
      im.withRelatedFromComponents(grouped.getOrElse(im.id, List.empty[Band]))
    }
  }

  def imagesToImagesWithRelated(
      images: List[Image]): ConnectionIO[List[Image.WithRelated]] = {
    (images map { _.id }).toNel match {
      case Some(ids) => {
        val allBandsIO = bandsForImages(ids)
        allBandsIO map { imagesAndBandsToImagesWithRelated(images, _) }
      }
      case _ =>
        List.empty[Image.WithRelated].pure[ConnectionIO]
    }
  }
}
