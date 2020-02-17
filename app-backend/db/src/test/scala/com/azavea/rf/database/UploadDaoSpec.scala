package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class UploadDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("list uploads") {
    UploadDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("insert an upload") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            upload: Upload.Create
        ) =>
          {
            val uploadInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              datasource <- unsafeGetRandomDatasource
              insertedUpload <- UploadDao.insert(
                fixupUploadCreate(dbUser, dbProject, datasource, upload),
                dbUser,
                0
              )
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

  test("insert an upload to a project") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            upload: Upload.Create
        ) =>
          {
            val uploadInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              datasource <- unsafeGetRandomDatasource
              uploadToInsert = upload.copy(
                owner = Some(user.id),
                datasource = datasource.id,
                projectId = Some(dbProject.id)
              )
              insertedUpload <- UploadDao.insert(uploadToInsert, dbUser, 0)
            } yield (insertedUpload, dbProject)

            val (dbUpload, dbProject) =
              uploadInsertIO.transact(xa).unsafeRunSync

            dbUpload.uploadStatus == upload.uploadStatus &&
            dbUpload.fileType == upload.fileType &&
            dbUpload.files == upload.files &&
            dbUpload.metadata == upload.metadata &&
            dbUpload.visibility == upload.visibility &&
            dbUpload.source == upload.source &&
            dbUpload.projectId == Some(dbProject.id) &&
            dbUpload.layerId == Some(dbProject.defaultLayerId)
          }
      }
    }
  }

  test("insert an upload to a project's non-default layer") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            upload: Upload.Create,
            projectLayerCreate: ProjectLayer.Create
        ) =>
          {
            val uploadInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              datasource <- unsafeGetRandomDatasource
              dbLayer <- ProjectLayerDao.insertProjectLayer(
                projectLayerCreate
                  .copy(projectId = Some(dbProject.id))
                  .toProjectLayer
              )
              uploadToInsert = upload.copy(
                owner = Some(user.id),
                datasource = datasource.id,
                projectId = Some(dbProject.id),
                layerId = Some(dbLayer.id)
              )
              insertedUpload <- UploadDao.insert(uploadToInsert, dbUser, 0)
            } yield (insertedUpload, dbProject, dbLayer)

            val (dbUpload, dbProject, dbLayer) =
              uploadInsertIO.transact(xa).unsafeRunSync

            dbUpload.uploadStatus == upload.uploadStatus &&
            dbUpload.fileType == upload.fileType &&
            dbUpload.files == upload.files &&
            dbUpload.metadata == upload.metadata &&
            dbUpload.visibility == upload.visibility &&
            dbUpload.source == upload.source &&
            dbUpload.projectId == Some(dbProject.id) &&
            dbUpload.layerId == Some(dbLayer.id)
          }
      }
    }
  }

  test("update an upload") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            insertUpload: Upload.Create,
            updateUpload: Upload.Create
        ) =>
          {
            val uploadInsertWithUserOrgProjectDatasourceIO = for {
              userOrgPlatProject <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              (dbUser, dbOrg, dbPlatform, dbProject) = userOrgPlatProject
              datasource <- unsafeGetRandomDatasource
              insertedUpload <- UploadDao.insert(
                fixupUploadCreate(dbUser, dbProject, datasource, insertUpload),
                dbUser,
                0
              )
            } yield
              (insertedUpload, dbUser, dbOrg, dbPlatform, dbProject, datasource)

            val uploadUpdateWithUploadIO = uploadInsertWithUserOrgProjectDatasourceIO flatMap {
              case (
                  dbUpload: Upload,
                  dbUser: User,
                  _: Organization,
                  dbPlatform: Platform,
                  dbProject: Project,
                  dbDatasource: Datasource
                  ) => {
                val uploadId = dbUpload.id
                val fixedUpUpdateUpload =
                  fixupUploadCreate(
                    dbUser,
                    dbProject,
                    dbDatasource,
                    updateUpload
                  ).toUpload(
                    dbUser,
                    (dbPlatform.id, false),
                    Some(dbPlatform.id),
                    0
                  )
                UploadDao.update(fixedUpUpdateUpload, uploadId) flatMap {
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
              uploadUpdateWithUploadIO.transact(xa).unsafeRunSync

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
