package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel.{User, Organization, Platform, Shape}
import com.rasterfoundry.common.datamodel.Generators.Implicits._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class ShapeDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("list shapes") {
    xa.use(t => ShapeDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  test("insert shapes") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         shapes: Seq[Shape.Create]) =>
          {
            val shapeInsertIO = for {
              (dbUser, _, _) <- insertUserOrgPlatform(user, org, platform)
              dbShapes <- ShapeDao.insertShapes(shapes map {
                fixupShapeCreate(dbUser, _)
              }, dbUser)
            } yield shapes
            xa.use(t => shapeInsertIO.transact(t))
              .unsafeRunSync
              .length == shapes.length
          }
      }
    }
  }

  test("update a shape") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         shapeInsert: Shape.Create,
         shapeUpdate: Shape.GeoJSON) =>
          {
            val shapeUpdateIO = for {
              (dbUser, _, _) <- insertUserOrgPlatform(user, org, platform)
              shape <- ShapeDao.insertShapes(List(shapeInsert), dbUser) map {
                _.head
              }
              affectedRows <- ShapeDao.updateShape(
                fixupShapeGeoJSON(dbUser, shape.toShape, shapeUpdate),
                shape.id,
                dbUser)
              fetched <- ShapeDao.unsafeGetShapeById(shape.id)
            } yield { (affectedRows, fetched) }

            val (affectedRows, updatedShape) =
              xa.use(t => shapeUpdateIO.transact(t)).unsafeRunSync

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
