package com.azavea.rf.thumbnail

import java.sql.Timestamp
import java.util.UUID

import scala.util.{Success, Failure}

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}
import slick.lifted.TableQuery

import com.azavea.rf.utils.Config
import com.azavea.rf.DBSpec
import com.azavea.rf.datamodel.latest.schema.tables._


class ThumbnailSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with DBSpec
    with Thumbnail {

  implicit val ec = system.dispatcher

  implicit val database = db

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
