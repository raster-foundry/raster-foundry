package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class UploadDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {
  test("list uploads") {
    UploadDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("insert an upload") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create, upload: Upload.Create) => {
          val uploadInsertIO = for {
            orgUserProject <- insertUserOrgProject(user, org, project)
            (dbOrg, dbUser, dbProject) = orgUserProject
            datasource <- unsafeGetRandomDatasource
            insertedUpload <- UploadDao.insert(fixupUploadCreate(dbUser, dbProject, datasource, upload), dbUser)
          } yield insertedUpload

          val dbUpload = uploadInsertIO.transact(xa).unsafeRunSync

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
        (user: User.Create, org: Organization.Create, platform: Platform, project: Project.Create,
         insertUpload: Upload.Create, updateUpload: Upload.Create) => {
          val uploadInsertWithUserOrgProjectDatasourceIO = for {
            userOrgPlatProject <- insertUserOrgPlatProject(user, org, platform, project)
            (dbUser, dbOrg, dbPlatform, dbProject) = userOrgPlatProject
            datasource <- unsafeGetRandomDatasource
            insertedUpload <- UploadDao.insert(fixupUploadCreate(dbUser, dbProject, datasource, insertUpload), dbUser)
          } yield (insertedUpload, dbUser, dbOrg, dbProject, datasource)

          val uploadUpdateWithUploadIO = uploadInsertWithUserOrgProjectDatasourceIO flatMap {
            case (dbUpload: Upload, dbUser: User, dbOrg: Organization, dbProject: Project, dbDatasource: Datasource) => {
              val uploadId = dbUpload.id
              val fixedUpUpdateUpload =
                fixupUploadCreate(dbUser, dbProject, dbDatasource, updateUpload).toUpload(dbUser)
              UploadDao.update(fixedUpUpdateUpload, uploadId, dbUser) flatMap {
                (affectedRows: Int) => {
                  UploadDao.unsafeGetUploadById(uploadId) map { (affectedRows, _) }
                }
              }
            }
          }

          val (affectedRows, updatedUpload) = uploadUpdateWithUploadIO.transact(xa).unsafeRunSync

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
