package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

import java.util.UUID

class ToolRunDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("create a tool run with a project id") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         projCreate: Project.Create,
         toolRunCreate: ToolRun.Create) =>
          {
            val toolRunInsertIO = for {
              (_, dbUser, dbProject) <- insertUserOrgProject(user,
                                                             org,
                                                             projCreate)
              withProjectId = toolRunCreate.copy(projectId = Some(dbProject.id))
              inserted <- ToolRunDao.insertToolRun(withProjectId, dbUser)
            } yield (withProjectId.projectId, inserted)

            val (projectIdCheck, insertedToolRun) =
              xa.use(t => toolRunInsertIO.transact(t)).unsafeRunSync

            assert(
              insertedToolRun.name == toolRunCreate.name,
              "Names should match after db"
            )
            assert(
              insertedToolRun.visibility == toolRunCreate.visibility,
              "Visibility should match after db"
            )
            assert(
              insertedToolRun.projectId == projectIdCheck,
              "ProjectIds should match after db"
            )
            assert(
              insertedToolRun.projectLayerId == toolRunCreate.projectLayerId,
              "ProjectLayerIds should match after db"
            )
            assert(
              insertedToolRun.templateId == toolRunCreate.templateId,
              "TemplateIds should match after db"
            )
            assert(
              insertedToolRun.executionParameters == toolRunCreate.executionParameters,
              "ExecutionParameters should match after db"
            )
            true
          }
      }
    }
  }

  test("create a tool run with a project layer id") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         projCreate: Project.Create,
         toolRunCreate: ToolRun.Create) =>
          {
            val toolRunInsertIO = for {
              (_, dbUser, dbProject) <- insertUserOrgProject(user,
                                                             org,
                                                             projCreate)
              withProjectLayerId = toolRunCreate.copy(
                projectLayerId = Some(dbProject.defaultLayerId))
              inserted <- ToolRunDao.insertToolRun(withProjectLayerId, dbUser)
            } yield (withProjectLayerId.projectLayerId, inserted)

            val (projectLayerIdCheck, insertedToolRun) =
              xa.use(t => toolRunInsertIO.transact(t)).unsafeRunSync

            assert(
              insertedToolRun.name == toolRunCreate.name,
              "Names should match after db"
            )
            assert(
              insertedToolRun.visibility == toolRunCreate.visibility,
              "Visibility should match after db"
            )
            assert(
              insertedToolRun.projectId == toolRunCreate.projectId,
              "ProjectIds should match after db"
            )
            assert(
              insertedToolRun.projectLayerId == projectLayerIdCheck,
              "ProjectLayerIds should match after db"
            )
            assert(
              insertedToolRun.templateId == toolRunCreate.templateId,
              "TemplateIds should match after db"
            )
            assert(
              insertedToolRun.executionParameters == toolRunCreate.executionParameters,
              "ExecutionParameters should match after db"
            )
            true
          }
      }
    }
  }

  test("create a tool run with a template id") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         templateCreate: Tool.Create,
         toolRunCreate: ToolRun.Create) =>
          {
            val toolRunInsertIO = for {
              (_, dbUser) <- insertUserAndOrg(user, org)
              dbTemplate <- ToolDao.insert(templateCreate, dbUser)
              withTemplateId = toolRunCreate.copy(
                templateId = Some(dbTemplate.id))
              inserted <- ToolRunDao.insertToolRun(withTemplateId, dbUser)
            } yield (withTemplateId.templateId, inserted)

            val (templateIdCheck, insertedToolRun) =
              xa.use(t => toolRunInsertIO.transact(t)).unsafeRunSync

            assert(
              insertedToolRun.name == toolRunCreate.name,
              "Names should match after db"
            )
            assert(
              insertedToolRun.visibility == toolRunCreate.visibility,
              "Visibility should match after db"
            )
            assert(
              insertedToolRun.projectId == toolRunCreate.projectId,
              "ProjectIds should match after db"
            )
            assert(
              insertedToolRun.projectLayerId == toolRunCreate.projectLayerId,
              "ProjectLayerIds should match after db"
            )
            assert(
              insertedToolRun.templateId == templateIdCheck,
              "TemplateIds should match after db"
            )
            assert(
              insertedToolRun.executionParameters == toolRunCreate.executionParameters,
              "ExecutionParameters should match after db"
            )
            true
          }
      }
    }
  }

  test("update tool run") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            toolRunCreate1: ToolRun.Create,
            toolRunCreate2: ToolRun.Create
        ) =>
          {
            val insertAndUpdateIO = for {
              (_, dbUser) <- insertUserAndOrg(user, org)
              inserted1 <- ToolRunDao.insertToolRun(toolRunCreate1, dbUser)
              inserted2 <- ToolRunDao.insertToolRun(toolRunCreate2, dbUser)
              _ <- ToolRunDao.updateToolRun(inserted2, inserted1.id, dbUser)
              fetched <- ToolRunDao.unsafeGetToolRunById(inserted1.id)
            } yield { (fetched, inserted2) }

            val (retrieved, updateRecord) =
              xa.use(t => insertAndUpdateIO.transact(t)).unsafeRunSync

            assert(
              retrieved.name == updateRecord.name,
              "Names should match after db"
            )
            assert(
              retrieved.visibility == updateRecord.visibility,
              "Visibility should match after db"
            )
            assert(
              retrieved.executionParameters == updateRecord.executionParameters,
              "ExecutionParameters should match after db"
            )
            true
          }
      }

    }
  }

  test("filter for tool runs with a project id") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         projCreate: Project.Create,
         toolRunCreate: ToolRun.Create) =>
          {
            val listIO = for {
              (_, dbUser, dbProject) <- insertUserOrgProject(user,
                                                             org,
                                                             projCreate)
              withProject = toolRunCreate.copy(projectId = Some(dbProject.id))
              queryParams = CombinedToolRunQueryParameters(
                toolRunParams = ToolRunQueryParameters(
                  projectId = Some(dbProject.id)
                )
              )
              otherQueryParams = CombinedToolRunQueryParameters(
                toolRunParams = ToolRunQueryParameters(
                  projectId = Some(UUID.randomUUID)
                )
              )
              _ <- ToolRunDao.insertToolRun(withProject, dbUser)
              listedGood <- ToolRunDao
                .authQuery(dbUser,
                           ObjectType.Analysis,
                           Some("owned"),
                           None,
                           None)
                .filter(queryParams)
                .list
              listedBad <- ToolRunDao
                .authQuery(dbUser,
                           ObjectType.Analysis,
                           Some("owned"),
                           None,
                           None)
                .filter(otherQueryParams)
                .list
            } yield { (listedGood, listedBad) }

            val (good, bad) = xa.use(t => listIO.transact(t)).unsafeRunSync
            assert(
              good.length == 1,
              "Only the tool run we just created should have come back from the good list")
            assert(good.head.name == toolRunCreate.name,
                   "And its name should match")
            assert(
              bad.length == 0,
              "Nothing should have come back from the project id filter that was random")

            true
          }
      }

    }
  }

  test("filter for tool runs with a template id") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         templateCreate: Tool.Create,
         toolRunCreate: ToolRun.Create) =>
          {
            val listIO = for {
              (_, dbUser) <- insertUserAndOrg(user, org)
              dbTemplate <- ToolDao.insert(templateCreate, dbUser)
              withTemplateId = toolRunCreate.copy(
                templateId = Some(dbTemplate.id))
              queryParams = CombinedToolRunQueryParameters(
                toolRunParams = ToolRunQueryParameters(
                  templateId = Some(dbTemplate.id)
                )
              )
              otherQueryParams = CombinedToolRunQueryParameters(
                toolRunParams = ToolRunQueryParameters(
                  templateId = Some(UUID.randomUUID)
                )
              )
              _ <- ToolRunDao.insertToolRun(withTemplateId, dbUser)
              listedGood <- ToolRunDao
                .authQuery(dbUser,
                           ObjectType.Analysis,
                           Some("owned"),
                           None,
                           None)
                .filter(queryParams)
                .list
              listedBad <- ToolRunDao
                .authQuery(dbUser,
                           ObjectType.Analysis,
                           Some("owned"),
                           None,
                           None)
                .filter(otherQueryParams)
                .list
            } yield { (listedGood, listedBad) }

            val (good, bad) = xa.use(t => listIO.transact(t)).unsafeRunSync
            assert(
              good.length == 1,
              "Only the tool run we just created should have come back from the good list")
            assert(good.head.name == toolRunCreate.name,
                   "And its name should match")
            assert(
              bad.length == 0,
              "Nothing should have come back from the project id filter that was random")
            true
          }
      }

    }
  }

  test("filter for tool runs with a project layer id") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         projCreate: Project.Create,
         toolRunCreate: ToolRun.Create) =>
          {
            val listIO = for {
              (_, dbUser, dbProject) <- insertUserOrgProject(user,
                                                             org,
                                                             projCreate)
              withProjectLayer = toolRunCreate.copy(
                projectLayerId = Some(dbProject.defaultLayerId))
              queryParams = CombinedToolRunQueryParameters(
                toolRunParams = ToolRunQueryParameters(
                  projectLayerId = Some(dbProject.defaultLayerId)
                )
              )
              otherQueryParams = CombinedToolRunQueryParameters(
                toolRunParams = ToolRunQueryParameters(
                  projectLayerId = Some(UUID.randomUUID)
                )
              )
              _ <- ToolRunDao.insertToolRun(withProjectLayer, dbUser)
              listedGood <- ToolRunDao
                .authQuery(dbUser,
                           ObjectType.Analysis,
                           Some("owned"),
                           None,
                           None)
                .filter(queryParams)
                .list
              listedBad <- ToolRunDao
                .authQuery(dbUser,
                           ObjectType.Analysis,
                           Some("owned"),
                           None,
                           None)
                .filter(otherQueryParams)
                .list
            } yield { (listedGood, listedBad) }

            val (good, bad) = xa.use(t => listIO.transact(t)).unsafeRunSync
            assert(
              good.length == 1,
              "Only the tool run we just created should have come back from the good list")
            assert(good.head.name == toolRunCreate.name,
                   "And its name should match")
            assert(
              bad.length == 0,
              "Nothing should have come back from the project id filter that was random")
            true
          }
      }

    }
  }
}
