package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class StacExportDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("inserting a Stac Export") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            stacExportCreate: StacExport.Create
        ) => {
          val createStacExportIO = for {
            dbUser <- UserDao.create(userCreate)
            dbProject <- AnnotationProjectDao
              .insert(annotationProjectCreate, dbUser)
            fixedStacExportCreate = fixupStacExportCreate(
              stacExportCreate,
              dbUser,
              dbProject
            )
            dbStacExport <- StacExportDao.create(
              fixedStacExportCreate,
              dbUser
            )
          } yield (dbUser, fixedStacExportCreate, dbStacExport)
          val (user, seCreate, se) =
            createStacExportIO.transact(xa).unsafeRunSync

          assert(
            user.id == se.owner,
            "Inserted StacExport owner should be the same as user"
          )
          assert(
            seCreate.name == se.name,
            "Sent and inserted StacExport name should be the same"
          )
          assert(
            se.exportLocation == None,
            "Inserted StacExport export location should be empty"
          )
          assert(
            se.exportStatus == ExportStatus.NotExported,
            "Inserted StacExport status should be NotExported"
          )
          assert(
            se.taskStatuses.toSet == seCreate.taskStatuses
              .map(_.toString)
              .toSet,
            "Sent and inserted StacExport taskStatuses should be the same"
          )
          assert(
            se.annotationProjectId.map(_ == seCreate.annotationProjectId).getOrElse(false),
            "Sent and inserted StacExport annotation project ids should be the same"
          )
          true
        }
      )
    }
  }

  test("getting a Stac Export by id") {
    check {
      forAll(
        (
            userCreate: User.Create,
            projectCreate: AnnotationProject.Create,
            stacExportCreate: StacExport.Create
        ) => {
          val selectStacExportIO = for {
            dbUser <- UserDao.create(userCreate)
            dbProject <- AnnotationProjectDao.insert(projectCreate, dbUser)
            fixedStacExportCreate = fixupStacExportCreate(
              stacExportCreate,
              dbUser,
              dbProject
            )
            dbStacExport <- StacExportDao.create(
              fixedStacExportCreate,
              dbUser
            )
            selectedStacExport <- StacExportDao
              .getById(dbStacExport.id)
          } yield (dbUser, dbStacExport, selectedStacExport)

          val (user, se, selectedSeO) =
            selectStacExportIO.transact(xa).unsafeRunSync

          selectedSeO match {
            case Some(selectedSe) =>
              assert(
                se.id == selectedSe.id,
                "Inserted and selected StacExport id should be the same"
              )
              assert(
                user.id == selectedSe.owner,
                "Selected StacExport owner should be the same as user"
              )
              assert(
                se.name == selectedSe.name,
                "Inserted and selected StacExport name should be the same"
              )
              assert(
                selectedSe.exportLocation == None,
                "Selected StacExport export location should be empty"
              )
              assert(
                selectedSe.exportStatus == ExportStatus.NotExported,
                "Selected StacExport status should be NotExported"
              )
              assert(
                se.taskStatuses.toSet == selectedSe.taskStatuses.toSet,
                "Inserted and selected StacExport taskStatuses should be the same"
              )
              assert(
                se.annotationProjectId == selectedSe.annotationProjectId,
                "Selected and inserted StacExport annotation project ids should be the same"
              )
              true
            case _ => false
          }
        }
      )
    }
  }

  test("updating a Stac Export") {
    check {
      forAll(
        (
            userCreate: User.Create,
            projectCreate: AnnotationProject.Create,
            stacExportCreate: StacExport.Create
        ) => {
          val updatetStacExportIO = for {
            dbUser <- UserDao.create(userCreate)
            dbProject <- AnnotationProjectDao.insert(projectCreate, dbUser)
            fixedStacExportCreate = fixupStacExportCreate(
              stacExportCreate,
              dbUser,
              dbProject
            )
            dbStacExport <- StacExportDao.create(
              fixedStacExportCreate,
              dbUser
            )
            updatedRowCount <- StacExportDao.update(
              dbStacExport.copy(
                exportStatus = ExportStatus.Exported,
                exportLocation = Some(""),
                taskStatuses = List()
              ),
              dbStacExport.id
            )
            selectedStacExport <- StacExportDao
              .unsafeGetById(dbStacExport.id)
          } yield {
            (dbUser, dbStacExport, updatedRowCount, selectedStacExport)
          }

          val (user, se, rowCount, selectedSe) =
            updatetStacExportIO.transact(xa).unsafeRunSync

          assert(
            rowCount == 1,
            "Should have one record updated"
          )
          assert(
            se.id == selectedSe.id,
            "Inserted and selected StacExport id should be the same"
          )
          assert(
            user.id == selectedSe.owner,
            "Selected StacExport owner should be the same as user"
          )
          assert(
            se.name == selectedSe.name,
            "Inserted and selected StacExport name should be the same"
          )
          assert(
            selectedSe.exportLocation == Some(""),
            "Selected StacExport export location should be updated"
          )
          assert(
            selectedSe.exportStatus == ExportStatus.Exported,
            "Selected StacExport status should be updated"
          )
          assert(
            se.taskStatuses.toSet == selectedSe.taskStatuses.toSet,
            "Updating task statuses should not change the record in DB"
          )
          assert(
            se.annotationProjectId == selectedSe.annotationProjectId,
            "Selected and inserted StacExport annotation project ids should be the same"
          )
          true
        }
      )
    }
  }

  test("deleting a Stac Export") {
    check {
      forAll(
        (
            userCreate: User.Create,
            projectCreate: AnnotationProject.Create,
            stacExportCreate: StacExport.Create
        ) => {
          val deletetStacExportIO = for {
            dbUser <- UserDao.create(userCreate)
            dbProject <- AnnotationProjectDao.insert(projectCreate, dbUser)
            fixedStacExportCreate = fixupStacExportCreate(
              stacExportCreate,
              dbUser,
              dbProject
            )
            dbStacExport <- StacExportDao.create(fixedStacExportCreate, dbUser)
            deletedRowCount <- StacExportDao
              .delete(dbStacExport.id)
            selectedStacExport <- StacExportDao
              .getById(dbStacExport.id)
          } yield { (deletedRowCount, selectedStacExport) }

          val (rowCount, selectedSe) =
            deletetStacExportIO.transact(xa).unsafeRunSync

          assert(
            rowCount == 1,
            "Should have one record deleted"
          )
          assert(
            selectedSe == None,
            "Inserted StacExport should be deleted from DB"
          )
          true
        }
      )
    }
  }

  test("listing Stac Export") {
    check {
      forAll(
        (
            userCreate: User.Create,
            projectCreate: AnnotationProject.Create,
            stacExportCreate1: StacExport.Create,
            stacExportCreate2: StacExport.Create,
            page: PageRequest,
            queryParams: StacExportQueryParameters
        ) => {
          val updatetStacExportIO = for {
            dbUser <- UserDao.create(userCreate)
            dbProject <- AnnotationProjectDao.insert(projectCreate, dbUser)
            fixedStacExportCreate1 = fixupStacExportCreate(
              stacExportCreate1,
              dbUser,
              dbProject
            )
            fixedStacExportCreate2 = fixupStacExportCreate(
              stacExportCreate2,
              dbUser,
              dbProject
            )
            dbStacExport1 <- StacExportDao
              .create(fixedStacExportCreate1, dbUser)
            _ <- StacExportDao.create(fixedStacExportCreate2, dbUser)
            _ <- StacExportDao.update(
              dbStacExport1.copy(
                exportStatus = ExportStatus.Exported
              ),
              dbStacExport1.id
            )
            paginatedStacExport <- StacExportDao
              .list(
                page,
                queryParams.copy(
                  exportStatus = Some("Exported"),
                  annotationProjectId = Some(dbProject.id)
                ),
                dbUser
              )
          } yield paginatedStacExport

          val paginatedResp =
            updatetStacExportIO.transact(xa).unsafeRunSync

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
