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
import scala.util.Random

class DatasourceDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("inserting a datasource") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         dsCreate: Datasource.Create) => {
          val createDsIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            dsInsert <- fixupDatasource(dsCreate, userInsert)
          } yield dsInsert
          val createDs = createDsIO.transact(xa).unsafeRunSync
          createDs.name == dsCreate.name
        }
      )
    }
  }

  test("getting a datasource by id") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         dsCreate: Datasource.Create) => {
          val getDsIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            dsInsert <- fixupDatasource(dsCreate, userInsert)
            dsGet <- DatasourceDao.getDatasourceById(dsInsert.id)
          } yield dsGet
          val getDs = getDsIO.transact(xa).unsafeRunSync
          getDs.get.name === dsCreate.name
        }
      )
    }
  }

  test("getting a datasource by id unsafely") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         dsCreate: Datasource.Create) => {
          val getDsUnsafeIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            dsInsert <- fixupDatasource(dsCreate, userInsert)
            dsGetUnsafe <- DatasourceDao.unsafeGetDatasourceById(dsInsert.id)
          } yield dsGetUnsafe
          val getDsUnsafe = getDsUnsafeIO.transact(xa).unsafeRunSync
          getDsUnsafe.name === dsCreate.name
        }
      )
    }
  }

  test("updating a datasource") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         dsCreate: Datasource.Create,
         dsUpdate: Datasource.Create) => {
          val updateDsIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            dsInsert <- fixupDatasource(dsCreate, userInsert)
            dsUpdated <- fixupDatasource(dsUpdate, userInsert)
            rowUpdated <- DatasourceDao.updateDatasource(dsUpdated,
                                                         dsInsert.id,
                                                         userInsert)
          } yield (rowUpdated, dsUpdated)
          val (rowUpdated, dsUpdated) = updateDsIO.transact(xa).unsafeRunSync
          rowUpdated == 1 &&
          dsUpdated.name == dsUpdate.name &&
          dsUpdated.visibility == dsUpdate.visibility &&
          dsUpdated.composites == dsUpdate.composites &&
          dsUpdated.extras == dsUpdate.extras &&
          dsUpdated.bands == dsUpdate.bands &&
          dsUpdated.licenseName == dsUpdate.licenseName
        }
      )
    }
  }

  test("deleting a datasource") {
    check {
      forAll(
        (userCreate: User.Create,
         orgCreate: Organization.Create,
         dsCreate: Datasource.Create) => {
          val deleteDsIO = for {
            orgAndUserInsert <- insertUserAndOrg(userCreate, orgCreate)
            (orgInsert, userInsert) = orgAndUserInsert
            dsInsert <- fixupDatasource(dsCreate, userInsert)
            rowDeleted <- DatasourceDao.deleteDatasource(dsInsert.id)
          } yield rowDeleted
          val deleteDsRowCount = deleteDsIO.transact(xa).unsafeRunSync
          deleteDsRowCount == 1
        }
      )
    }
  }

  test("listing datasources") {
    DatasourceDao.query.list.transact(xa).unsafeRunSync.length >= 0
  }

  test("only owner of a datasource can delete a datasource") {
    check {
      forAll(
        (userCreate: User.Create,
         ownerCreate: User.Create,
         orgCreate: Organization.Create,
         dsCreate: Datasource.Create) => {
          val isDsDeletableIO = for {
            orgAndOwnerInsert <- insertUserAndOrg(ownerCreate, orgCreate)
            (orgInsert, ownerInsert) = orgAndOwnerInsert
            userInsert <- UserDao.create(userCreate)
            dsInsert <- fixupDatasource(dsCreate, ownerInsert)
            isDeletableUser <- DatasourceDao.isDeletable(dsInsert.id,
                                                         userInsert)
            isDeletableOwner <- DatasourceDao.isDeletable(dsInsert.id,
                                                          ownerInsert)
          } yield { (isDeletableUser, isDeletableOwner) }

          val (isDeletableUser, isDeletableOwner) =
            isDsDeletableIO.transact(xa).unsafeRunSync

          assert(
            !isDeletableUser,
            "Non-owner of a datasource should not be able to delete a datasource")
          assert(isDeletableOwner,
                 "Owner of a datasource should be able to delete a datasource")
          true
        }
      )
    }
  }

  test("isDeletable should return false if a datasource is shared") {
    check {
      forAll(
        (userCreate: User.Create,
         ownerCreate: User.Create,
         orgCreate: Organization.Create,
         dsCreate: Datasource.Create) => {
          val isDsDeletableIO = for {
            orgAndOwnerInsert <- insertUserAndOrg(ownerCreate, orgCreate)
            (orgInsert, ownerInsert) = orgAndOwnerInsert
            dsInsert <- fixupDatasource(dsCreate, ownerInsert)
            _ <- DatasourceDao.addPermission(
              dsInsert.id,
              ObjectAccessControlRule(SubjectType.All, None, ActionType.View))
            isDeletable <- DatasourceDao.isDeletable(dsInsert.id, ownerInsert)
          } yield { isDeletable }

          val isDeletable = isDsDeletableIO.transact(xa).unsafeRunSync
          assert(!isDeletable,
                 "isDeletable should return false if a datasource is shared")
          true
        }
      )
    }
  }

  test(
    "isDeletable should return false if a datasource has an upload not completed/failed/aborted") {
    check {
      forAll(
        (userCreate: User.Create,
         ownerCreate: User.Create,
         orgCreate: Organization.Create,
         dsCreate: Datasource.Create,
         project: Project.Create,
         upload: Upload.Create) => {
          @SuppressWarnings(Array("TraversableHead"))
          val isDsDeletableIO = for {
            orgUserProject <- insertUserOrgProject(userCreate,
                                                   orgCreate,
                                                   project)
            (dbOrg, dbUser, dbProject) = orgUserProject
            datasource <- fixupDatasource(dsCreate, dbUser)
            statuses = List(UploadStatus.Created,
                            UploadStatus.Uploading,
                            UploadStatus.Uploaded,
                            UploadStatus.Queued,
                            UploadStatus.Processing)
            status = Random.shuffle(statuses).head
            _ <- UploadDao.insert(
              fixupUploadCreate(dbUser,
                                dbProject,
                                datasource,
                                upload.copy(uploadStatus = status)),
              dbUser)
            isDeletable <- DatasourceDao.isDeletable(datasource.id, dbUser)
          } yield { isDeletable }

          val isDeletable = isDsDeletableIO.transact(xa).unsafeRunSync

          assert(
            !isDeletable,
            "isDeletable should return false if a datasource has an upload not in completed/failed/aborted")
          true
        }
      )
    }
  }

}
