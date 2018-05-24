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
        (user: User.Create, org: Organization.Create, project: Project.Create, aoi: AOI.Create) => {
          val aoiInsertIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject, aoi), dbUser)
            }
          }
          val insertedAoi = aoiInsertIO.transact(xa).unsafeRunSync

          assert(insertedAoi.area == aoi.area, "Area should round trip with equality")
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
         aoiInsert: AOI.Create, aoiUpdate: AOI.Create) => {
          val aoiInsertWithOrgUserProjectIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject, aoiInsert), dbUser) map {
                (_, dbOrg, dbUser, dbProject)
              }
            }
          }

          val aoiUpdateWithAoi = aoiInsertWithOrgUserProjectIO flatMap {
            case (dbAoi: AOI, dbOrg: Organization, dbUser: User, dbProject: Project) => {
              val aoiId = dbAoi.id
              val fixedUpUpdateAoi = fixupAoiCreate(dbUser, dbProject, aoiUpdate).copy(id = aoiId)
              AoiDao.updateAOI(fixedUpUpdateAoi, aoiId, dbUser) flatMap {
                (affectedRows: Int) => {
                  AoiDao.unsafeGetAoiById(aoiId) map { (affectedRows, _) }
                }
              }
            }
          }

          val (affectedRows, updatedAoi) = aoiUpdateWithAoi.transact(xa).unsafeRunSync

          assert(affectedRows == 1, "Only one row should be updated")
          assert(updatedAoi.area == aoiUpdate.area, "Area should be updated")
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
        (user: User.Create, org: Organization.Create, project: Project.Create, aoi: AOI.Create) => {
          val aoiDeleteIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject, aoi), dbUser) map {
                (_, dbUser)
              }
            }
          } flatMap {
            case (dbAoi: AOI, dbUser: User) => {
              AoiDao.deleteAOI(dbAoi.id, dbUser)
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
         project1: Project.Create, aois1: List[AOI.Create],
         project2: Project.Create, aois2: List[AOI.Create]) => {
          val aoisInsertWithProjectUserIO = for {
            userOrgProj1 <- insertUserOrgProject(user, org, project1)
            (dbOrg, dbUser, dbProject1) = userOrgProj1
            dbProject2 <- ProjectDao.insertProject(fixupProjectCreate(dbUser, project2), dbUser)
            dbAois1 <- aois1.traverse {
              (aoi: AOI.Create) => {
                AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject1, aoi), dbUser)
              }
            }
            _ <- aois2.traverse {
              (aoi: AOI.Create) => {
                AoiDao.createAOI(fixupAoiCreate(dbUser, dbProject2, aoi), dbUser)
              }
            }
          } yield { (dbAois1, dbProject1, dbUser) }

          val aoisForProject = aoisInsertWithProjectUserIO flatMap {
            case (dbAois: List[AOI], dbProject: Project, dbUser: User) => {
              AoiDao.listAOIs(dbProject.id, dbUser, PageRequest(0, 1000, Map.empty)) map {
                (dbAois, _)
              }
            }
          }

          val (dbAois, listedAois) = aoisForProject.transact(xa).unsafeRunSync
          (dbAois.toSet map { (aoi: AOI) => aoi.area }) == (listedAois.results.toSet map { (aoi: AOI) => aoi.area })
        }
      }
    }
  }

}

