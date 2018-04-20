package com.azavea.rf.database

import com.azavea.rf.datamodel.Organization
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers


class OrganizationDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig {

  // createOrganization
  test("insert an organization from an Organization.Create") {
    check {
      forAll(
        (orgCreate: Organization.Create) => {
          OrganizationDao.createOrganization(orgCreate).transact(xa).unsafeRunSync.name == orgCreate.name
        }
      )
    }
  }

  // getOrganizationById
  test("get an organization by id") {
    check {
      forAll(
        (orgCreate: Organization.Create) => {
          val retrievedNameIO = OrganizationDao.createOrganization(orgCreate) flatMap {
            (org: Organization) => {
              OrganizationDao.getOrganizationById(org.id) map {
                (retrievedO: Option[Organization]) => retrievedO map { _.name }
              }
            }
          }
          retrievedNameIO.transact(xa).unsafeRunSync.get == orgCreate.name
        }
      )
    }
  }

  // updateOrganization
  test("update an organization") {
    check {
      forAll(
        (orgCreate: Organization.Create, newName: String) => {
          val withoutNull = newName.filter( _ != '\u0000' ).mkString
          val insertOrgIO = OrganizationDao.createOrganization(orgCreate)
          val insertAndUpdateIO =  insertOrgIO flatMap {
            (org: Organization) => {
              OrganizationDao.update(org.copy(name = withoutNull), org.id) flatMap {
                case (affectedRows: Int) => {
                  OrganizationDao.unsafeGetOrganizationById(org.id) map {
                    (retrievedOrg: Organization) => (affectedRows, retrievedOrg.name)
                  }
                }
              }
            }
          }
          val (affectedRows, updatedName) = insertAndUpdateIO.transact(xa).unsafeRunSync
          (affectedRows == 1) && (updatedName == withoutNull)
        }
      )
    }
  }

  // list organizations
  test("list organizations") {
    OrganizationDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }
}

