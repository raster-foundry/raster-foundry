package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel.{Organization, Platform, Shape, User}

import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ShapeDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with LazyLogging
    with PropTestHelpers {

  test("list shapes") {
    ShapeDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("insert shapes") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            shapes: Seq[Shape.Create]
        ) =>
          {
            val shapeInsertIO = for {
              (dbUser, _, _) <- insertUserOrgPlatform(user, org, platform)
              dbShapes <- ShapeDao.insertShapes(shapes map {
                fixupShapeCreate(dbUser, _)
              }, dbUser)
              _ = logger.trace(s"Inserted $dbShapes shapes")
            } yield shapes
            shapeInsertIO.transact(xa).unsafeRunSync.length == shapes.length
          }
      }
    }
  }

  test("update a shape") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            shapeInsert: Shape.Create,
            shapeUpdate: Shape.GeoJSON
        ) =>
          {
            val shapeUpdateIO = for {
              (dbUser, _, _) <- insertUserOrgPlatform(user, org, platform)
              shape <- ShapeDao.insertShapes(List(shapeInsert), dbUser) map {
                _.head
              }
              affectedRows <- ShapeDao.updateShape(
                fixupShapeGeoJSON(dbUser, shape.toShape, shapeUpdate),
                shape.id
              )
              fetched <- ShapeDao.unsafeGetShapeById(shape.id)
            } yield { (affectedRows, fetched) }

            val (affectedRows, updatedShape) =
              shapeUpdateIO.transact(xa).unsafeRunSync

            val shapeUpdateShape = shapeUpdate.toShape

            affectedRows == 1 &&
            updatedShape.name == shapeUpdateShape.name &&
            updatedShape.description == shapeUpdateShape.description &&
            updatedShape.geometry == shapeUpdateShape.geometry

          }
      }
    }
  }
}
