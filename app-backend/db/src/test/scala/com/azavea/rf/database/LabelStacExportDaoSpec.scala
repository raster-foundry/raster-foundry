package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.Generators.Implicits._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class LabelStacExportDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("inserting a Label Stac Export") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         platform: Platform,
         projectCreate: Project.Create,
         labelStacExportCreate: LabelStacExport.Create) => {
          val createLabelStacExportIO = for {
            (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(userCreate,
                                                                  orgCreate,
                                                                  platform,
                                                                  projectCreate)
            fixedLabelStacExportCreate = fixupLabelStacExportCreate(
              labelStacExportCreate,
              dbUser,
              dbProject)
            dbLabelStacExport <- LabelStacExportDao.create(
              fixedLabelStacExportCreate,
              dbUser
            )
          } yield { (dbUser, fixedLabelStacExportCreate, dbLabelStacExport) }
          val (user, lseCreate, lse) =
            createLabelStacExportIO.transact(xa).unsafeRunSync

          assert(
            user.id == lse.owner,
            "Inserted LabelStacExport owner should be the same as user"
          )
          assert(
            lseCreate.name == lse.name,
            "Sent and inserted LabelStacExport name should be the same"
          )
          assert(
            lse.exportLocation == None,
            "Inserted LabelStacExport export location should be empty"
          )
          assert(
            lse.exportStatus == ExportStatus.NotExported,
            "Inserted LabelStacExport status should be NotExported"
          )
          assert(
            lse.layerDefinition == lseCreate.layerDefinition,
            "Sent and inserted LabelStacExport layer definition should be the same"
          )
          assert(
            lse.isUnion == lseCreate.isUnion,
            "Sent and inserted LabelStacExport isUnion should be the same"
          )
          assert(
            lse.taskStatuses.toSet == lseCreate.taskStatuses.map(_.toString).toSet,
            "Sent and inserted LabelStacExport taskStatuses should be the same"
          )
          true
        }
      )
    }
  }

  test("getting a Label Stac Export by id") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         platform: Platform,
         projectCreate: Project.Create,
         labelStacExportCreate: LabelStacExport.Create) => {
          val selectLabelStacExportIO = for {
            (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(userCreate,
                                                                  orgCreate,
                                                                  platform,
                                                                  projectCreate)
            fixedLabelStacExportCreate = fixupLabelStacExportCreate(
              labelStacExportCreate,
              dbUser,
              dbProject)
            dbLabelStacExport <- LabelStacExportDao.create(
              fixedLabelStacExportCreate,
              dbUser
            )
            selectedLabelStacExport <- LabelStacExportDao
              .getLabelStacExportById(dbLabelStacExport.id)
          } yield { (dbUser, dbLabelStacExport, selectedLabelStacExport) }

          val (user, lse, selectedLseO) =
            selectLabelStacExportIO.transact(xa).unsafeRunSync

          selectedLseO match {
            case Some(selectedLse) =>
              assert(
                lse.id == selectedLse.id,
                "Inserted and selected LabelStacExport id should be the same"
              )
              assert(
                user.id == selectedLse.owner,
                "Selected LabelStacExport owner should be the same as user"
              )
              assert(
                lse.name == selectedLse.name,
                "Inserted and selected LabelStacExport name should be the same"
              )
              assert(
                selectedLse.exportLocation == None,
                "Selected LabelStacExport export location should be empty"
              )
              assert(
                selectedLse.exportStatus == ExportStatus.NotExported,
                "Selected LabelStacExport status should be NotExported"
              )
              assert(
                lse.layerDefinition == selectedLse.layerDefinition,
                "Inserted and selected LabelStacExport layer definition should be the same"
              )
              assert(
                lse.isUnion == selectedLse.isUnion,
                "Inserted and selected LabelStacExport isUnion should be the same"
              )
              assert(
                lse.taskStatuses.toSet == selectedLse.taskStatuses.toSet,
                "Inserted and selected LabelStacExport taskStatuses should be the same"
              )
              true
            case _ => false
          }
        }
      )
    }
  }

  test("updating a Label Stac Export") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         platform: Platform,
         projectCreate: Project.Create,
         labelStacExportCreate: LabelStacExport.Create) => {
           val updatetLabelStacExportIO = for {
            (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(userCreate,
                                                                 orgCreate,
                                                                 platform,
                                                                 projectCreate)
            fixedLabelStacExportCreate = fixupLabelStacExportCreate(
              labelStacExportCreate,
              dbUser,
              dbProject)
            dbLabelStacExport <- LabelStacExportDao.create(
              fixedLabelStacExportCreate,
              dbUser
            )
            updatedRowCount <- LabelStacExportDao.update(
              dbLabelStacExport.copy(
                exportStatus = ExportStatus.Exported,
                exportLocation = Some(""),
                taskStatuses = List()
              ),
              dbLabelStacExport.id,
              dbUser
            )
            selectedLabelStacExport <- LabelStacExportDao
              .unsafeGetLabelStacExportById(dbLabelStacExport.id)
          } yield { (dbUser, dbLabelStacExport, updatedRowCount, selectedLabelStacExport) }

          val (user, lse, rowCount, selectedLse) =
            updatetLabelStacExportIO.transact(xa).unsafeRunSync

          assert(
            rowCount == 1,
            "Should have one record updated"
          )
          assert(
            lse.id == selectedLse.id,
            "Inserted and selected LabelStacExport id should be the same"
          )
          assert(
            user.id == selectedLse.owner,
            "Selected LabelStacExport owner should be the same as user"
          )
          assert(
            lse.name == selectedLse.name,
            "Inserted and selected LabelStacExport name should be the same"
          )
          assert(
            selectedLse.exportLocation == Some(""),
            "Selected LabelStacExport export location should be updated"
          )
          assert(
            selectedLse.exportStatus == ExportStatus.Exported,
            "Selected LabelStacExport status should be updated"
          )
          assert(
            lse.layerDefinition == selectedLse.layerDefinition,
            "Inserted and selected LabelStacExport layer definition should be the same"
          )
          assert(
            lse.isUnion == selectedLse.isUnion,
            "Inserted and selected LabelStacExport isUnion should be the same"
          )
          assert(
            lse.taskStatuses.toSet == selectedLse.taskStatuses.toSet,
            "Updating task statuses should not change the record in DB"
          )
          true
        }
      )
    }
  }

  test("deleting a Label Stac Export") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         platform: Platform,
         projectCreate: Project.Create,
         labelStacExportCreate: LabelStacExport.Create) => {
           val deletetLabelStacExportIO = for {
            (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(userCreate,
                                                                 orgCreate,
                                                                 platform,
                                                                 projectCreate)
            fixedLabelStacExportCreate = fixupLabelStacExportCreate(
              labelStacExportCreate,
              dbUser,
              dbProject)
            dbLabelStacExport <- LabelStacExportDao.create(
              fixedLabelStacExportCreate,
              dbUser)
            deletedRowCount <- LabelStacExportDao
              .delete(dbLabelStacExport.id)
            selectedLabelStacExport <- LabelStacExportDao
              .getLabelStacExportById(dbLabelStacExport.id)
          } yield { (deletedRowCount, selectedLabelStacExport) }

          val (rowCount, selectedLse) =
            deletetLabelStacExportIO.transact(xa).unsafeRunSync

          assert(
            rowCount == 1,
            "Should have one record deleted"
          )
          assert(
            selectedLse == None,
            "Inserted LabelStacExport should be deleted from DB"
          )
          true
        }
      )
    }
  }

  test("listing Label Stac Export") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         platform: Platform,
         projectCreate: Project.Create,
         labelStacExportCreate1: LabelStacExport.Create,
         labelStacExportCreate2: LabelStacExport.Create,
         page: PageRequest,
         queryParams: LabelStacExportQueryParameters) => {
           val updatetLabelStacExportIO = for {
            (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(userCreate,
                                                                 orgCreate,
                                                                 platform,
                                                                 projectCreate)
            fixedLabelStacExportCreate1 = fixupLabelStacExportCreate(
              labelStacExportCreate1,
              dbUser,
              dbProject)
            fixedLabelStacExportCreate2 = fixupLabelStacExportCreate(
              labelStacExportCreate2,
              dbUser,
              dbProject)
            dbLabelStacExport1 <- LabelStacExportDao.create(
              fixedLabelStacExportCreate1,
              dbUser)
            _ <- LabelStacExportDao.create(
              fixedLabelStacExportCreate2,
              dbUser)
            _ <- LabelStacExportDao.update(
              dbLabelStacExport1.copy(
                exportStatus = ExportStatus.Exported
              ),
              dbLabelStacExport1.id,
              dbUser
            )
            paginatedLabelStacExport <- LabelStacExportDao
              .list(page, queryParams.copy(exportStatus = Some("Exported")))
          } yield paginatedLabelStacExport

          val paginatedResp =
            updatetLabelStacExportIO.transact(xa).unsafeRunSync

          assert(
            paginatedResp.count == 1,
            "Should have one record matching the export status filter"
          )
          true
        }
      )
    }
  }
}
