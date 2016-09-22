package com.azavea.rf.thumbnail

import akka.actor.ActorSystem
import com.azavea.rf._
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.thumbnail._
import com.azavea.rf.utils._
import java.sql._
import java.util.UUID

import org.scalatest._

import scala.concurrent.Future
import scala.util.{Success, Failure, Try}
import slick.lifted.TableQuery

class ThumbnailSpec extends PGUtilsSpec with Thumbnail {

  val thumbnails = TableQuery[Thumbnails]
  val uuid = new UUID(123456789, 123456789)
  val baseThumbnailRow = ThumbnailsRow(
    uuid,
    new Timestamp(1234687268),
    new Timestamp(1234687268),
    uuid,
    128,
    128,
    "large",
    uuid,
    "https://website.com"
  )

  "Creating a row" should {
    "add a row to the table" ignore {
      val result = insertThumbnail(baseThumbnailRow)
      assert(result === Success)
    }
  }

  "Getting a row" should {
    "return the expected row" ignore {
      assert(getThumbnail(uuid) === baseThumbnailRow)
    }
  }

  "Updating a row" should {
    "change the expected values" ignore {
      val newThumbnailsRow = ThumbnailsRow(
        uuid,
        new Timestamp(1234687268),
        new Timestamp(1234687268),
        uuid,
        256,
        128,
        "large",
        uuid,
        "https://website.com"
      )
      val result = updateThumbnail(newThumbnailsRow, uuid)
      assert(result === 1)
      getThumbnail(uuid) map {
        case Some(resp) => assert(resp.widthPx === 256)
        case _ => Failure(new Exception("Field not updated successfully"))
      }
    }
  }

  "Deleting a row" should {
    "remove a row from the table" ignore {
      val result = deleteThumbnail(uuid)
      assert(result === 1)
    }
  }

}
