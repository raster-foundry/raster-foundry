package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

import io.circe._
import io.circe.syntax._
import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.implicits._
import cats.syntax.either._
import com.lonelyplanet.akka.http.extensions.PageRequest
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import geotrellis.slick._
import geotrellis.vector._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers


class AoiDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  test("list AOIs") {
    AoiDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("insert an AOI") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create, aoi: AOI.Create, shape: Shape.Create) => {
          val aoiInsertIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              for {
                shape <- ShapeDao.insertShape(shape, dbUser)
                aoi <- AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject, aoi, shape), dbUser)
              } yield (shape, aoi)
            }
          }
          val (insertedShape, insertedAoi) = aoiInsertIO.transact(xa).unsafeRunSync

          assert(insertedAoi.shape == insertedShape.id, "Shape should round trip with equality")
          assert(insertedAoi.filters == aoi.filters, "Filters should round trip with equality")
          assert(insertedAoi.startTime == aoi.startTime, "Start time should round trip with equality")
          assert(insertedAoi.approvalRequired == aoi.approvalRequired,
                 "Approval required should round trip with equality")
          true
        }
      }
    }
  }

  test("update an AOI") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create,
         aoiInsert: AOI.Create, aoiUpdate: AOI.Create, shapeInsert: Shape.Create, shapeUpdate: Shape.Create) => {
          val aoiInsertWithOrgUserProjectIO = for {
            orgUserProject <- insertUserOrgProject(user, org, project)
            (dbOrg, dbUser, dbProject) = orgUserProject
            shape <- ShapeDao.insertShape(shapeInsert, dbUser)
            dbAoi <- AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject, aoiInsert, shape), dbUser)
            newShape <- ShapeDao.insertShape(shapeUpdate, dbUser)
            updatedRows <- AoiDao.updateAOI(fixupAoiCreate(dbUser, dbProject, aoiUpdate, newShape).copy(id=dbAoi.id), dbUser)
            updatedAoi <- AoiDao.unsafeGetAoiById(dbAoi.id)
          } yield (dbAoi, shape, updatedRows, updatedAoi, newShape) //shape, aoi, dbOrg, dbUser, dbProject, update, updatedRows, updatedAoi)
          val (originalAoi, originalShape, affectedRows, updatedAoi, updatedShape) =
            aoiInsertWithOrgUserProjectIO.transact(xa).unsafeRunSync

          assert(affectedRows == 1, "Only one row should be updated")
          assert(updatedAoi.shape == updatedShape.id, "Area should be updated")
          assert(updatedAoi.filters == aoiUpdate.filters, "Filters should be updated")
          assert(updatedAoi.isActive == aoiUpdate.isActive, "Active status should be updated")
          assert(updatedAoi.approvalRequired == aoiUpdate.approvalRequired, "Approval should be updated")
          assert(updatedAoi.startTime == aoiUpdate.startTime, "Start time should be updated")
          true
        }
      }
    }
  }

  test("delete an AOI") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create, aoi: AOI.Create, shape: Shape.Create) => {
          val aoiDeleteIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              for {
                dbShape <- ShapeDao.insertShape(shape, dbUser)
                dbAoi <- AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject, aoi, dbShape), dbUser)
                deletedShapes <- AoiDao.deleteAOI(dbAoi.id)
              } yield deletedShapes
            }
          }

          aoiDeleteIO.transact(xa).unsafeRunSync == 1
        }
      }
    }
  }

  test("list AOIs for a project") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create,
         project1: Project.Create, aois1: List[AOI.Create], shape: Shape.Create,
         project2: Project.Create, aois2: List[AOI.Create]) => {
          val aoisInsertWithProjectUserIO = for {
            userOrgProj1 <- insertUserOrgProject(user, org, project1)
            (dbOrg, dbUser, dbProject1) = userOrgProj1
            dbProject2 <- ProjectDao.insertProject(fixupProjectCreate(dbUser, project2), dbUser)
            dbShape <- ShapeDao.insertShape(shape, dbUser)
            dbAois1 <- aois1.traverse {
              (aoi: AOI.Create) => {
                AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject1, aoi, dbShape), dbUser)
              }
            }
            _ <- aois2.traverse {
              (aoi: AOI.Create) => {
                AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject2, aoi, dbShape), dbUser)
              }
            }
          } yield { (dbAois1, dbProject1, dbUser) }

          val aoisForProject = aoisInsertWithProjectUserIO flatMap {
            case (dbAois: List[AOI], dbProject: Project, dbUser: User) => {
              AoiDao.listAOIs(dbProject.id, PageRequest(0, 1000, Map.empty)) map {
                (dbAois, _)
              }
            }
          }

          val (dbAois, listedAois) = aoisForProject.transact(xa).unsafeRunSync
          (dbAois.toSet map { (aoi: AOI) => aoi.id }) == (listedAois.results.toSet map { (aoi: AOI) => aoi.id })
        }
      }
    }
  }

  test("list authorized AOIs") {
    check {
      forAll {
        (user1: User.Create, user2: User.Create,
         shape: Shape.Create,
         project1: Project.Create, aois1: List[AOI.Create],
         project2: Project.Create, aois2: List[AOI.Create],
         page: PageRequest) => {
          val aoisInsertAndListIO = for {
            dbUser1 <- UserDao.create(user1)
            dbUser2 <- UserDao.create(user2)
            dbProject1 <- ProjectDao.insertProject(fixupProjectCreate(dbUser1, project1).copy(visibility = Visibility.Private), dbUser1)
            dbProject2 <- ProjectDao.insertProject(fixupProjectCreate(dbUser2, project2).copy(visibility = Visibility.Private), dbUser2)
            dbShape <- ShapeDao.insertShape(shape, dbUser1)
            dbAois1 <- aois1 traverse {
              (aoi: AOI.Create) => {
                AoiDao.createAOI(fixupAoiCreate(dbUser1, dbProject1, aoi, dbShape), dbUser1)
              }
            }
            _ <- aois2 traverse {
              (aoi: AOI.Create) => {
                AoiDao.createAOI(fixupAoiCreate(dbUser2, dbProject2, aoi, dbShape), dbUser2)
              }
            }
            listedAois <- AoiDao.listAuthorizedAois(dbUser1, AoiQueryParameters(), page)
          } yield (dbAois1, listedAois)
          val (insertedAois, listedAois) = aoisInsertAndListIO.transact(xa).unsafeRunSync
          val insertedAoiAreaSet = insertedAois map { _.id } toSet
          val listedAoisAreaSet = listedAois.results map { _.id } toSet

          assert(listedAoisAreaSet.intersect(insertedAoiAreaSet) == listedAoisAreaSet,
                 "Listed AOI areas are a strict subset of inserted AOI areas")
          true
        }
      }
    }
  }

}
