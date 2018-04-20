package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import doobie.postgres._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers
import io.circe._
import io.circe.syntax._
import java.util.UUID

class WorkspaceDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  test("inserting a workspace") {
    check {
      forAll (
        (workspaceCreate: Workspace.Create, userCreate: User.Create, orgCreate: Organization.Create) => {
          val insertWorkspaceIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            workspaceInsert <- WorkspaceDao.insert(
              fixupWorkspace(workspaceCreate, orgInsert, userInsert), userInsert)
          } yield (workspaceInsert, orgInsert, userInsert)

          val (insertWorkspace, orgInsert, userInsert) = insertWorkspaceIO.transact(xa).unsafeRunSync

          insertWorkspace.name == workspaceCreate.name &&
            insertWorkspace.organizationId == orgInsert.id &&
            insertWorkspace.createdBy == userInsert.id &&
            insertWorkspace.modifiedBy == userInsert.id &&
            insertWorkspace.owner == userInsert.id &&
            insertWorkspace.organizationId == orgInsert.id &&
            insertWorkspace.description == workspaceCreate.description
        }
      )
    }
  }

  test("getting a workspace by ID") {
    check {
      forAll (
        (workspaceCreate: Workspace.Create, userCreate: User.Create, orgCreate: Organization.Create) => {
          val getWorkspaceIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            workspaceInsert <- WorkspaceDao.insert(
              fixupWorkspace(workspaceCreate, orgInsert, userInsert), userInsert)
            workspaceGetOp <- WorkspaceDao.getById(workspaceInsert.id, userInsert)
          } yield (workspaceGetOp, orgInsert, userInsert)

          val (workspaceGetOp, orgInsert, userInsert) = getWorkspaceIO.transact(xa).unsafeRunSync
          val workspaceGet = workspaceGetOp.get

          workspaceGet.name == workspaceCreate.name &&
            workspaceGet.organizationId == orgInsert.id &&
            workspaceGet.createdBy == userInsert.id &&
            workspaceGet.modifiedBy == userInsert.id &&
            workspaceGet.owner == userInsert.id &&
            workspaceGet.organizationId == orgInsert.id &&
            workspaceGet.description == workspaceCreate.description
        }
      )
    }
  }

  test("updating a workspace") {
    check {
      forAll (
        (workspaceUpdate: Workspace.Update, workspaceCreate: Workspace.Create,
          userCreate: User.Create, orgCreate: Organization.Create,
          analysisCreate: Analysis.Create
        ) => {
          val insertWorkspaceIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            analysisInsert <- AnalysisDao.insertAnalysis(
              fixupAnalysis(analysisCreate, orgInsert, userInsert), userInsert)
            workspaceInsert <- WorkspaceDao.insert(
              fixupWorkspace(workspaceCreate, orgInsert, userInsert), userInsert)
          } yield (workspaceInsert, analysisInsert, orgInsert, userInsert)

          val updateWorkspaceIO = insertWorkspaceIO flatMap {
            case (workspace: Workspace, analysisInsert: Analysis, org: Organization, user: User) => {
              val workspaceUpdated = workspaceUpdate.copy(
                id = workspace.id,
                owner = user.id,
                organizationId = org.id,
                activeAnalysis = Some(analysisInsert.id)
              )
              WorkspaceDao.update(workspaceUpdated, workspace.id, user) flatMap {
                (affectedRowCount: Int) => {
                  WorkspaceDao.getById(workspace.id, user) map {
                    (affectedRowCount, _)
                  }
                }
              }
            }
          }

          val (affectedRowCount, workspaceUpdatedOp) = updateWorkspaceIO.transact(xa).unsafeRunSync
          val workspaceUpdated = workspaceUpdatedOp.get

          affectedRowCount == 1 &&
            workspaceUpdated.name == workspaceUpdate.name &&
            workspaceUpdated.description == workspaceUpdate.description
        }
      )
    }
  }

  test("deleting a workspace") {
    check {
      forAll (
        (workspaceCreate: Workspace.Create, userCreate: User.Create, orgCreate: Organization.Create) => {
          val deleteWorkspaceIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            workspaceInsert <- WorkspaceDao.insert(
              fixupWorkspace(workspaceCreate, orgInsert, userInsert), userInsert)
            deletedRowCount <- WorkspaceDao.delete(workspaceInsert.id, userInsert)
          } yield deletedRowCount

          val deletedRowCount = deleteWorkspaceIO.transact(xa).unsafeRunSync

          deletedRowCount == 1
        }
      )
    }
  }

  test("adding a workspace analysis") {
    check {
      forAll (
        (workspaceCreate: Workspace.Create, userCreate: User.Create,
          orgCreate: Organization.Create, analysisCreate: Analysis.Create) => {
          val addAnalysisInWorkspaceIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            workspaceInsert <- WorkspaceDao.insert(
              fixupWorkspace(workspaceCreate, orgInsert, userInsert), userInsert)
            analysisInsert <- WorkspaceDao.addAnalysis(
              workspaceInsert.id,
              fixupAnalysis(analysisCreate, orgInsert, userInsert),
              userInsert)
          } yield (workspaceInsert, analysisInsert, orgInsert, userInsert)

          val (workspaceInsert, analysisInsertOp, orgInsert, userInsert) = addAnalysisInWorkspaceIO.transact(xa).unsafeRunSync
          val analysisInsert = analysisInsertOp.get

          (Some(analysisInsert.name) == analysisCreate.name ||  analysisInsert .name == "") &&
            analysisInsert.visibility == analysisCreate.visibility &&
            analysisInsert.executionParameters == analysisCreate.executionParameters &&
            (Some(analysisInsert.readonly) == analysisCreate.readonly || analysisInsert.readonly == false)
        }
      )
    }
  }

  test("deleting a workspace analysis") {
    check {
      forAll (
        (workspaceCreate: Workspace.Create, userCreate: User.Create,
          orgCreate: Organization.Create, analysisCreate: Analysis.Create) => {
          val deleteAnalysisInWorkspaceIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            workspaceInsert <- WorkspaceDao.insert(
              fixupWorkspace(workspaceCreate, orgInsert, userInsert), userInsert)
            analysisInsert <- WorkspaceDao.addAnalysis(
              workspaceInsert.id,
              fixupAnalysis(analysisCreate, orgInsert, userInsert),
              userInsert)
            deletedRowCount <- WorkspaceDao.deleteAnalysis(workspaceInsert.id, analysisInsert.get.id, userInsert)
          } yield deletedRowCount

          val deletedRowCount = deleteAnalysisInWorkspaceIO.transact(xa).unsafeRunSync

          deletedRowCount == 1
        }
      )
    }
  }

  test("getting analyses") {
    check {
      forAll (
        (workspaceCreate: Workspace.Create, userCreate: User.Create,
          orgCreate: Organization.Create, analysisCreate: Analysis.Create) => {
          val getAnalysesInWorkspaceIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            workspaceInsert <- WorkspaceDao.insert(
              fixupWorkspace(workspaceCreate, orgInsert, userInsert), userInsert)
            analysisInsert <- WorkspaceDao.addAnalysis(
              workspaceInsert.id,
              fixupAnalysis(analysisCreate, orgInsert, userInsert),
              userInsert)
            analysisGet <- WorkspaceDao.getAnalyses(workspaceInsert.id, userInsert)
          } yield (analysisGet, analysisInsert)

          val (analysisGet, analysisInsert) = getAnalysesInWorkspaceIO.transact(xa).unsafeRunSync
          val analysisGetOne = analysisGet.get.head

          analysisGetOne.name == analysisInsert.get.name &&
            analysisGetOne.visibility == analysisInsert.get.visibility &&
            analysisGetOne.executionParameters == analysisInsert.get.executionParameters &&
            analysisGetOne.readonly == analysisInsert.get.readonly
        }
      )
    }
  }

}
