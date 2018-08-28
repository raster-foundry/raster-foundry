package com.azavea.rf.database

import com.azavea.rf.database.util.Page
import com.azavea.rf.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import java.util.UUID

import com.azavea.rf.database.Implicits._

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
