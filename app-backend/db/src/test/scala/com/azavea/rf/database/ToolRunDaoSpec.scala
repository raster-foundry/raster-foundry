package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import doobie._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

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
         projCreate: Project.Create,
         toolRunCreate: ToolRun.Create) =>
          {
            true
          }
      }
    }
  }
}
