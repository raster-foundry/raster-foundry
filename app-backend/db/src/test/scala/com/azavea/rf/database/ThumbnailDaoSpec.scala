package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class ThumbnailDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("list thumbnails") {
    ThumbnailDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("insert a thumbnail") {
    check {
      forAll {
        (org: Organization.Create,
         user: User.Create,
         platform: Platform,
         scene: Scene.Create,
         thumbnail: Thumbnail) =>
          {
            val thumbnailInsertIO = insertUserOrgPlatScene(user,
                                                           org,
                                                           platform,
                                                           scene) flatMap {
              case (dbOrg: Organization,
                    dbUser: User,
                    dbPlatform: Platform,
                    dbScene: Scene.WithRelated) => {
                ThumbnailDao.insert(fixupThumbnail(dbScene, thumbnail))
              }
            }
            val insertedThumbnail =
              thumbnailInsertIO.transact(xa).unsafeRunSync

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
         platform: Platform,
         scene: Scene.Create,
         thumbnails: List[Thumbnail]) =>
          {
            val thumbnailsInsertIO = insertUserOrgPlatScene(user,
                                                            org,
                                                            platform,
                                                            scene) flatMap {
              case (dbOrg: Organization,
                    dbUser: User,
                    dbPlatform: Platform,
                    dbScene: Scene.WithRelated) => {
                ThumbnailDao.insertMany(thumbnails map {
                  fixupThumbnail(dbScene, _)
                })
              }
            }
            thumbnailsInsertIO.transact(xa).unsafeRunSync == thumbnails.length
          }
      }
    }
  }

  test("update a thumbnail") {
    check {
      forAll {
        (org: Organization.Create,
         user: User.Create,
         platform: Platform,
         scene: Scene.Create,
         insertThumbnail: Thumbnail,
         updateThumbnail: Thumbnail) =>
          {
            val thumbnailInsertIO = insertUserOrgPlatScene(user,
                                                           org,
                                                           platform,
                                                           scene) flatMap {
              case (dbOrg: Organization,
                    dbUser: User,
                    dbPlatform: Platform,
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
              thumbnailUpdateWithThumbnailIO.transact(xa).unsafeRunSync
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
