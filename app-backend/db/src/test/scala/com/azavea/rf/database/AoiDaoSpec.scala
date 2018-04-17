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
        (user: User.Create, org: Organization.Create, project: Project.Create, aoi: AOI) => {
          val aoiInsertIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              AoiDao.createAOI(fixupAoi(dbUser, dbOrg, aoi), dbProject.id, dbUser)
            }
          }
          val insertedAoi = aoiInsertIO.transact(xa).unsafeRunSync

          insertedAoi.area == aoi.area &&
            insertedAoi.filters == aoi.filters
        }
      }
    }
  }

  test("update an AOI") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create, aoiInsert: AOI, aoiUpdate: AOI) => {
          val aoiInsertWithOrgUserIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              AoiDao.createAOI(fixupAoi(dbUser, dbOrg, aoiInsert), dbProject.id, dbUser) map {
                (_, dbOrg, dbUser)
              }
            }
          }

          val aoiUpdateWithAoi = aoiInsertWithOrgUserIO flatMap {
            case (dbAoi: AOI, dbOrg: Organization, dbUser: User) => {
              val aoiId = dbAoi.id
              val fixedUpUpdateAoi = fixupAoi(dbUser, dbOrg, aoiUpdate).copy(id = aoiId)
              AoiDao.updateAOI(fixedUpUpdateAoi, aoiId, dbUser) flatMap {
                (affectedRows: Int) => {
                  AoiDao.unsafeGetAoiById(aoiId, dbUser) map { (affectedRows, _) }
                }
              }
            }
          }

          val (affectedRows, updatedAoi) = aoiUpdateWithAoi.transact(xa).unsafeRunSync

          affectedRows == 1 &&
            updatedAoi.area == aoiUpdate.area &&
            updatedAoi.filters == aoiUpdate.filters

        }
      }
    }
  }

  test("delete an AOI") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create, aoi: AOI) => {
          val aoiDeleteIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              AoiDao.createAOI(fixupAoi(dbUser, dbOrg, aoi), dbProject.id, dbUser) map {
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
        (user: User.Create, org: Organization.Create, project: Project.Create, aois: List[AOI]) => {
          val aoisInsertWithProjectUserIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              aois.traverse((aoi: AOI) => AoiDao.createAOI(fixupAoi(dbUser, dbOrg, aoi), dbProject.id, dbUser)) map {
                (_, dbProject, dbUser)
              }
            }
          }

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

