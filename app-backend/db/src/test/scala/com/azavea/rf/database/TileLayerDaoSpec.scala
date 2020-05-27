package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TileLayerDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("list tile layers for a project") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val listIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao
              .insert(annotationProjectCreate, user)
            listedReal <- TileLayerDao.listByProjectId(inserted.id)
            listedBogus <- TileLayerDao.listByProjectId(UUID.randomUUID)
          } yield { (inserted.tileLayers, listedReal, listedBogus) }

          val (insertedLayers, listedReal, listedBogus) =
            listIO.transact(xa).unsafeRunSync

          assert(insertedLayers === listedReal,
                 "Inserted layers and listed layers for project match")
          assert(listedBogus === Nil,
                 "List for a bogus project id returned nothing")

          true
        }
      )
    }
  }
}
