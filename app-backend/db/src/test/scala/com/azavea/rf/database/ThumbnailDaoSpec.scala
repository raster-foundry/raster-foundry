package com.azavea.rf.database

import com.azavea.rf.datamodel.{Organization, Thumbnail, ThumbnailSize}
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._
import java.util.UUID


class ThumbnailDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("types") { check(ThumbnailDao.selectF.query[Thumbnail]) }

  // This is a scene id from the development data. If this test starts failing for non-
  // obvious reasons, it probably means this scene is no longer part of development
  // data for some reason. This isn't ideal, but the notion of a "default" scene doesn't
  // make too much sense, so it's something to get the tests to run for now.
  val sceneId : UUID = UUID.fromString("a9ce69c2-f87e-4119-863d-d570afb53983")
  val makeThumbnail = (org : Organization, thumbnailSize : ThumbnailSize) => {
    Thumbnail.Create(
      org.id,
      thumbnailSize,
      256,
      256,
      sceneId,
      "https://url.com"
    ).toThumbnail
  }

  test("insertion") {
    val transaction = for {
      org <- rootOrgQ
      user <- defaultUserQ
      smallThumbnail = makeThumbnail(org, ThumbnailSize.Small)
      largeThumbnail = makeThumbnail(org, ThumbnailSize.Large)
      squareThumbnail = makeThumbnail(org, ThumbnailSize.Square)
      resultSmall <- ThumbnailDao.insert(smallThumbnail)
      resultLarge <- ThumbnailDao.insert(largeThumbnail)
      resultSquare <- ThumbnailDao.insert(squareThumbnail)
    } yield (resultSmall, resultLarge, resultSquare)

    val (small, large, square) = transaction.transact(xa).unsafeRunSync
    small.thumbnailSize shouldBe ThumbnailSize.Small
    large.thumbnailSize shouldBe ThumbnailSize.Large
    square.thumbnailSize shouldBe ThumbnailSize.Square
  }
}

