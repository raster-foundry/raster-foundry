package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers
import io.circe._
import io.circe.syntax._
import java.util.UUID


/** We only need to test inserting a single image and listing images because inserting
  *  many is tested in inserting a scene from a Scene.Create
  */
class ImageDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  test("insert a single image") {
    check {
      forAll(
        (user: User.Create, org: Organization.Create, scene: Scene.Create, image: Image.Banded) => {
          val sceneInsertIO = for {
            orgAndUser <- insertUserAndOrg(user, org)
            (insertedOrg, insertedUser) = orgAndUser
            datasource <- unsafeGetRandomDatasource
            insertedScene <- SceneDao.insert(
              fixupSceneCreate(insertedUser, datasource, scene), insertedUser
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
          val insertedImage = imageInsertIO.transact(xa).unsafeRunSync.get
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
        (user: User.Create, org: Organization.Create, scene: Scene.Create, imageBanded: Image.Banded, imageUpdate: Image) => {
          val sceneInsertIO = for {
            orgAndUser <- insertUserAndOrg(user, org)
            (insertedOrg, insertedUser) = orgAndUser
            datasource <- unsafeGetRandomDatasource
            insertedScene <- SceneDao.insert(
              fixupSceneCreate(insertedUser, datasource, scene), insertedUser
            )
          } yield (insertedScene, insertedUser)
          val imageInsertIO = sceneInsertIO flatMap {
            case (swr: Scene.WithRelated, dbUser: User) => {
              ImageDao.insertImage(
                fixupImageBanded(dbUser.id, swr.id, imageBanded),
                dbUser
              ) map { (_, dbUser) }
            }
          }
          val imageUpdateWithUpdatedImageIO = imageInsertIO flatMap {
            case (imageO: Option[Image.WithRelated], dbUser: User) => {
              val inserted = imageO.get
              val imageId = inserted.id
              val sceneId = inserted.scene
              val origOwner = inserted.owner
              val fixedUp = fixupImage(origOwner, sceneId, imageUpdate)
              ImageDao.updateImage(
                fixedUp, imageId, dbUser
              ) flatMap {
                (affectedRows: Int) => {
                  val updatedImage = ImageDao.unsafeGetImage(imageId)
                  updatedImage map { (affectedRows, _) }
                }
              }
            }
          }

          val (affectedRows, updatedImage) = imageUpdateWithUpdatedImageIO.transact(xa).unsafeRunSync
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

