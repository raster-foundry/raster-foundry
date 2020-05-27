package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

/** We only need to test inserting a single image and listing images because inserting
  *  many is tested in inserting a scene from a Scene.Create
  */
class ImageDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("insert a single image") {
    check {
      forAll(
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            scene: Scene.Create,
            image: Image.Banded
        ) => {
          val sceneInsertIO = for {
            (insertedUser, _, _) <- insertUserOrgPlatform(user, org, platform)
            datasource <- unsafeGetRandomDatasource
            insertedScene <- SceneDao.insert(
              fixupSceneCreate(insertedUser, datasource, scene),
              insertedUser
            )
          } yield (insertedScene, insertedUser)
          val imageInsertIO = sceneInsertIO flatMap {
            case (swr: Scene.WithRelated, dbUser: User) => {
              ImageDao.insertImage(
                image.copy(scene = swr.id, owner = Some(swr.owner)),
                dbUser
              )
            }
          }
          val insertedImage =
            imageInsertIO.transact(xa).unsafeRunSync.get
          insertedImage.rawDataBytes == image.rawDataBytes &&
          insertedImage.visibility == image.visibility &&
          insertedImage.filename == image.filename &&
          insertedImage.sourceUri == image.sourceUri &&
          insertedImage.imageMetadata == image.imageMetadata &&
          insertedImage.resolutionMeters == image.resolutionMeters &&
          insertedImage.metadataFiles == image.metadataFiles
        }
      )
    }
  }

  test("update an image") {
    check {
      forAll(
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            scene: Scene.Create,
            imageBanded: Image.Banded,
            imageUpdate: Image
        ) => {
          val sceneInsertIO = for {
            (insertedUser, _, _) <- insertUserOrgPlatform(user, org, platform)
            datasource <- unsafeGetRandomDatasource
            insertedScene <- SceneDao.insert(
              fixupSceneCreate(insertedUser, datasource, scene),
              insertedUser
            )
          } yield (insertedScene, insertedUser)
          val imageInsertIO = sceneInsertIO flatMap {
            case (swr: Scene.WithRelated, dbUser: User) => {
              ImageDao.insertImage(
                fixupImageBanded(dbUser.id, swr.id, imageBanded),
                dbUser
              )
            }
          }
          val imageUpdateWithUpdatedImageIO = imageInsertIO flatMap {
            case imageO: Option[Image.WithRelated] => {
              val inserted = imageO.get
              val imageId = inserted.id
              val sceneId = inserted.scene
              val origOwner = inserted.owner
              val fixedUp = fixupImage(origOwner, sceneId, imageUpdate)
              ImageDao.updateImage(
                fixedUp,
                imageId
              ) flatMap { (affectedRows: Int) =>
                {
                  val updatedImage = ImageDao.unsafeGetImage(imageId)
                  updatedImage map { (affectedRows, _) }
                }
              }
            }
          }

          val (affectedRows, updatedImage) =
            imageUpdateWithUpdatedImageIO.transact(xa).unsafeRunSync
          affectedRows == 1 &&
          updatedImage.rawDataBytes == imageUpdate.rawDataBytes &&
          updatedImage.visibility == imageUpdate.visibility &&
          updatedImage.filename == imageUpdate.filename &&
          updatedImage.sourceUri == imageUpdate.sourceUri &&
          updatedImage.imageMetadata == imageUpdate.imageMetadata &&
          updatedImage.resolutionMeters == imageUpdate.resolutionMeters &&
          updatedImage.metadataFiles == imageUpdate.metadataFiles
        }
      )
    }
  }

  test("list images") {
    ImageDao.query.list.transact(xa).unsafeRunSync
  }
}
