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
              fixupSceneCreate(insertedUser, insertedOrg, datasource, scene), insertedUser
            )
          } yield (insertedScene, insertedUser)
          val imageInsertIO = sceneInsertIO flatMap {
            case (swr: Scene.WithRelated, dbUser: User) => {
              ImageDao.insertImage(
                image.copy(scene = swr.id, organizationId = swr.organizationId, owner = Some(swr.owner)),
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

  test("list images") {
    ImageDao.query.list.transact(xa).unsafeRunSync
  }
}

