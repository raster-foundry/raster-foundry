package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class UploadDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("list uploads") {
    xa.use(t => UploadDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  test("insert an upload") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         upload: Upload.Create) =>
          {
            val uploadInsertIO = for {
              orgUserProject <- insertUserOrgPlatProject(user,
                                                         org,
                                                         platform,
                                                         project)
              (dbUser, dbOrg, _, dbProject) = orgUserProject
              datasource <- unsafeGetRandomDatasource
              insertedUpload <- UploadDao.insert(
                fixupUploadCreate(dbUser, dbProject, datasource, upload),
                dbUser)
            } yield insertedUpload

            val dbUpload = xa.use(t => uploadInsertIO.transact(t)).unsafeRunSync

            dbUpload.uploadStatus == upload.uploadStatus &&
            dbUpload.fileType == upload.fileType &&
            dbUpload.files == upload.files &&
            dbUpload.metadata == upload.metadata &&
            dbUpload.visibility == upload.visibility &&
            dbUpload.source == upload.source
          }
      }
    }
  }

  test("update an upload") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         insertUpload: Upload.Create,
         updateUpload: Upload.Create) =>
          {
            val uploadInsertWithUserOrgProjectDatasourceIO = for {
              userOrgPlatProject <- insertUserOrgPlatProject(user,
                                                             org,
                                                             platform,
                                                             project)
              (dbUser, dbOrg, dbPlatform, dbProject) = userOrgPlatProject
              datasource <- unsafeGetRandomDatasource
              insertedUpload <- UploadDao.insert(
                fixupUploadCreate(dbUser, dbProject, datasource, insertUpload),
                dbUser)
            } yield
              (insertedUpload, dbUser, dbOrg, dbPlatform, dbProject, datasource)

            val uploadUpdateWithUploadIO = uploadInsertWithUserOrgProjectDatasourceIO flatMap {
              case (dbUpload: Upload,
                    dbUser: User,
                    dbOrg: Organization,
                    dbPlatform: Platform,
                    dbProject: Project,
                    dbDatasource: Datasource) => {
                val uploadId = dbUpload.id
                val fixedUpUpdateUpload =
                  fixupUploadCreate(dbUser,
                                    dbProject,
                                    dbDatasource,
                                    updateUpload).toUpload(dbUser,
                                                           (dbPlatform.id,
                                                            false),
                                                           Some(dbPlatform.id))
                UploadDao.update(fixedUpUpdateUpload, uploadId, dbUser) flatMap {
                  (affectedRows: Int) =>
                    {
                      UploadDao.unsafeGetUploadById(uploadId) map {
                        (affectedRows, _)
                      }
                    }
                }
              }
            }

            val (affectedRows, updatedUpload) =
              xa.use(t => uploadUpdateWithUploadIO.transact(t)).unsafeRunSync

            affectedRows == 1 &&
            updatedUpload.uploadStatus == updateUpload.uploadStatus &&
            updatedUpload.fileType == updateUpload.fileType &&
            updatedUpload.uploadType == updateUpload.uploadType &&
            updatedUpload.metadata == updateUpload.metadata &&
            updatedUpload.visibility == updateUpload.visibility &&
            updatedUpload.projectId == updateUpload.projectId &&
            updatedUpload.source == updateUpload.source
          }
      }
    }
  }
}
