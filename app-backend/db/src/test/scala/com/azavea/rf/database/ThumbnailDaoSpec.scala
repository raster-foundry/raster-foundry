package com.rasterfoundry.database

import com.rasterfoundry.datamodel.Generators.Implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers
import java.util.UUID

class ThumbnailDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("list thumbnails") {
    xa.use(t => ThumbnailDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  test("insert a thumbnail") {
    check {
      forAll {
        (org: Organization.Create,
         user: User.Create,
         scene: Scene.Create,
         thumbnail: Thumbnail) =>
          {
            val thumbnailInsertIO = insertUserOrgScene(user, org, scene) flatMap {
              case (dbOrg: Organization,
                    dbUser: User,
                    dbScene: Scene.WithRelated) => {
                ThumbnailDao.insert(fixupThumbnail(dbScene, thumbnail))
              }
            }
            val insertedThumbnail =
              xa.use(t => thumbnailInsertIO.transact(t)).unsafeRunSync

            insertedThumbnail.widthPx == thumbnail.widthPx &&
            insertedThumbnail.heightPx == thumbnail.heightPx &&
            insertedThumbnail.url == thumbnail.url &&
            insertedThumbnail.thumbnailSize == thumbnail.thumbnailSize
          }
      }
    }
  }

  test("insert many thumbnails") {
    check {
      forAll {
        (org: Organization.Create,
         user: User.Create,
         scene: Scene.Create,
         thumbnails: List[Thumbnail]) =>
          {
            val thumbnailsInsertIO = insertUserOrgScene(user, org, scene) flatMap {
              case (dbOrg: Organization,
                    dbUser: User,
                    dbScene: Scene.WithRelated) => {
                ThumbnailDao.insertMany(thumbnails map {
                  fixupThumbnail(dbScene, _)
                })
              }
            }
            xa.use(t => thumbnailsInsertIO.transact(t))
              .unsafeRunSync == thumbnails.length
          }
      }
    }
  }

  test("update a thumbnail") {
    check {
      forAll {
        (org: Organization.Create,
         user: User.Create,
         scene: Scene.Create,
         insertThumbnail: Thumbnail,
         updateThumbnail: Thumbnail) =>
          {
            val thumbnailInsertIO = insertUserOrgScene(user, org, scene) flatMap {
              case (dbOrg: Organization,
                    dbUser: User,
                    dbScene: Scene.WithRelated) => {
                ThumbnailDao.insert(fixupThumbnail(dbScene, insertThumbnail))
              }
            }

            val thumbnailUpdateWithThumbnailIO = thumbnailInsertIO flatMap {
              (dbThumbnail: Thumbnail) =>
                {
                  val withFks = updateThumbnail.copy(
                    id = dbThumbnail.id,
                    sceneId = dbThumbnail.sceneId
                  )
                  ThumbnailDao.update(withFks, dbThumbnail.id) flatMap {
                    (affectedRows: Int) =>
                      {
                        ThumbnailDao.unsafeGetThumbnailById(dbThumbnail.id) map {
                          (affectedRows, _)
                        }
                      }
                  }
                }
            }

            val (affectedRows, updatedThumbnail) =
              xa.use(t => thumbnailUpdateWithThumbnailIO.transact(t))
                .unsafeRunSync
            affectedRows == 1 &&
            updatedThumbnail.widthPx == updateThumbnail.widthPx &&
            updatedThumbnail.heightPx == updateThumbnail.heightPx &&
            updatedThumbnail.url == updateThumbnail.url &&
            updatedThumbnail.thumbnailSize == updateThumbnail.thumbnailSize
          }
      }
    }
  }

}
