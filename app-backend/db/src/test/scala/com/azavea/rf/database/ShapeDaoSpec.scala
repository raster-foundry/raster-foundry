package com.rasterfoundry.database

import com.rasterfoundry.datamodel.{User, Organization, Shape}
import com.rasterfoundry.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import cats.syntax.option._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

import geotrellis.vector.{MultiPolygon, Polygon, Point}

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
         shapes: Seq[Shape.Create]) =>
          {
            val shapeInsertIO = insertUserAndOrg(user, org) flatMap {
              case (dbOrg: Organization, dbUser: User) => {
                ShapeDao.insertShapes(
                  shapes map { fixupShapeCreate(dbUser, _) },
                  dbUser)
              }
            }
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
         shapeInsert: Shape.Create,
         shapeUpdate: Shape.GeoJSON) =>
          {
            val shapeInsertWithUserAndOrgIO = insertUserAndOrg(user, org) flatMap {
              case (dbOrg: Organization, dbUser: User) => {
                ShapeDao.insertShapes(List(shapeInsert) map {
                  fixupShapeCreate(dbUser, _)
                }, dbUser) map { (shapes: Seq[Shape.GeoJSON]) =>
                  (shapes.head.toShape, dbUser, dbOrg)
                }
              }
            }
            val updateWithShapeIO = shapeInsertWithUserAndOrgIO flatMap {
              case (insertShape: Shape, dbUser: User, dbOrg: Organization) => {
                ShapeDao.updateShape(fixupShapeGeoJSON(dbUser,
                                                       insertShape,
                                                       shapeUpdate),
                                     insertShape.id,
                                     dbUser) flatMap { (affectedRows: Int) =>
                  {
                    ShapeDao.unsafeGetShapeById(insertShape.id) map {
                      (affectedRows, _)
                    }
                  }
                }
              }
            }

            val (affectedRows, updatedShape) =
              xa.use(t => updateWithShapeIO.transact(t)).unsafeRunSync

            val shapeUpdateShape = shapeUpdate.toShape

            affectedRows == 1 &&
            updatedShape.name == shapeUpdateShape.name &&
            updatedShape.description == shapeUpdateShape.description &&
            updatedShape.geometry == shapeUpdateShape.geometry

          }
      }
    }
  }

  test("get a shape by id") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, shape: Shape.Create) =>
          {
            val shapeInsertWithUserIO = insertUserAndOrg(user, org) flatMap {
              case (dbOrg: Organization, dbUser: User) => {
                ShapeDao.insertShapes(List(shape) map {
                  fixupShapeCreate(dbUser, _)
                }, dbUser) map {
                  (_, dbUser)
                }
              }
            }

            val shapeByIdIO = shapeInsertWithUserIO flatMap {
              case (shapes, dbUser) => {
                // safe because we just put it there -- errors here mean insert is broken
                val insertedShape = shapes.head.toShape
                ShapeDao.getShapeById(insertedShape.id) map {
                  _.get == insertedShape
                }
              }
            }

            xa.use(t => shapeByIdIO.transact(t)).unsafeRunSync
          }
      }
    }
  }
}
