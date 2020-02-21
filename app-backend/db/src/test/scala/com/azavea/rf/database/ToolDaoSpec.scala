package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class ToolDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("selection types") {
    ToolDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("list tools") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            toolCreates: List[Tool.Create]
        ) =>
          {
            val toolListIO = for {
              (dbUser, _, _) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform
              )
              dbTools <- toolCreates traverse { toolCreate =>
                ToolDao.insert(toolCreate, dbUser)
              }
              listed <- ToolDao
                .authQuery(
                  dbUser,
                  ObjectType.Template,
                  ownershipTypeO = Some("owned")
                )
                .list
            } yield (dbTools, listed)

            val (inserted, listed) =
              toolListIO.transact(xa).unsafeRunSync

            assert(
              toolCreates.length == inserted.length,
              "all of the tools were inserted into the db"
            )
            assert(
              inserted.length == listed.length,
              "counts of inserted and listed tools match"
            )
            assert(Set(toolCreates map { _.title }: _*) == Set(listed map {
              _.title
            }: _*), "titles of listed tools are the same as the Tool.Creates")
            true
          }
      }
    }
  }

  test("update a tool") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            toolCreate1: Tool.Create
        ) =>
          {
            val updateIO = for {
              (dbUser, _, _) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform
              )
              dbTool1 <- ToolDao.insert(toolCreate1, dbUser)
              dbTool2 <- ToolDao.insert(toolCreate1, dbUser)
              _ <- ToolDao.update(dbTool2, dbTool1.id)
              fetched <- ToolDao.query.filter(dbTool1.id).select
            } yield (dbTool2, fetched)

            val (updateTool, fetched) =
              updateIO.transact(xa).unsafeRunSync
            assert(
              fetched.title == updateTool.title,
              "Title of fetched tool should be the title of the udpate tool"
            )
            assert(
              fetched.description == updateTool.description,
              "Description of fetched tool should be the description of the udpate tool"
            )
            assert(
              fetched.requirements == updateTool.requirements,
              "Requirements of fetched tool should be the requirements of the udpate tool"
            )
            assert(
              fetched.visibility == updateTool.visibility,
              "Visibility of fetched tool should be the visibility of the udpate tool"
            )
            assert(
              fetched.compatibleDataSources == updateTool.compatibleDataSources,
              "CompatibleDataSources of fetched tool should be the compatibleDataSources of the udpate tool"
            )
            assert(
              fetched.stars == updateTool.stars,
              "Stars of fetched tool should be the stars of the udpate tool"
            )
            assert(
              fetched.singleSource == updateTool.singleSource,
              "SingleSource of fetched tool should be the singleSource of the udpate tool"
            )
            true
          }
      }
    }
  }
}
