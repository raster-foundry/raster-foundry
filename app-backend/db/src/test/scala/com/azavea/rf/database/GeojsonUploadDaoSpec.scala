package com.azavea.rf.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class GeojsonUploadDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("list geojson uploads") {
    GeojsonUploadDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("insert a geojson upload") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            geojsonUploadCreate: GeojsonUpload.Create,
            projectCreate: Project.Create,
            projectLayerCreate: ProjectLayer.Create,
            annotationGroupCreate: AnnotationGroup.Create
        ) =>
          {
            val uploadInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                projectCreate
              )
              dbLayer <- ProjectLayerDao.insertProjectLayer(
                projectLayerCreate
                  .copy(projectId = Some(dbProject.id))
                  .toProjectLayer
              )
              dbAnnotationGroup <- AnnotationGroupDao.createAnnotationGroup(
                dbProject.id,
                annotationGroupCreate,
                dbUser
              )
              insertedGeojsonUpload <- GeojsonUploadDao.insert(
                geojsonUploadCreate,
                dbProject.id,
                dbLayer.id,
                dbAnnotationGroup.id,
                dbUser
              )
            } yield insertedGeojsonUpload

            val dbUpload = uploadInsertIO.transact(xa).unsafeRunSync

            dbUpload.uploadStatus should be(geojsonUploadCreate.uploadStatus)
            dbUpload.fileType should be(geojsonUploadCreate.fileType)
            dbUpload.files should be(geojsonUploadCreate.files)
            true
          }
      }
    }
  }

  test("update a geojson upload") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            geojsonUploadCreate: GeojsonUpload.Create,
            projectCreate: Project.Create,
            projectLayerCreate: ProjectLayer.Create,
            annotationGroupCreate: AnnotationGroup.Create
        ) =>
          {
            val uploadInsertAndUpdateIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                projectCreate
              )
              dbLayer <- ProjectLayerDao.insertProjectLayer(
                projectLayerCreate
                  .copy(projectId = Some(dbProject.id))
                  .toProjectLayer
              )
              dbAnnotationGroup <- AnnotationGroupDao.createAnnotationGroup(
                dbProject.id,
                annotationGroupCreate,
                dbUser
              )
              insertedGeojsonUpload <- GeojsonUploadDao.insert(
                geojsonUploadCreate,
                dbProject.id,
                dbLayer.id,
                dbAnnotationGroup.id,
                dbUser
              )
              _ <- GeojsonUploadDao.update(
                insertedGeojsonUpload.copy(
                  uploadStatus = UploadStatus.Processing,
                  fileType = FileType.Geotiff
                ),
                dbProject.id,
                dbLayer.id,
                dbAnnotationGroup.id,
                insertedGeojsonUpload.id,
                dbUser
              )
              updatedGeojsonUpload <- GeojsonUploadDao.getLayerUpload(
                dbProject.id,
                dbLayer.id,
                dbAnnotationGroup.id,
                insertedGeojsonUpload.id
              )
            } yield (insertedGeojsonUpload, updatedGeojsonUpload)

            val (dbUpload, dbUpdatedUploadO) =
              uploadInsertAndUpdateIO.transact(xa).unsafeRunSync

            dbUpdatedUploadO should not be (None)
            val dbUpdatedUpload = dbUpdatedUploadO.get

            dbUpload.id should be(dbUpdatedUpload.id)
            dbUpload.fileType should be(dbUpdatedUpload.fileType)
            dbUpload.createdAt should be(dbUpdatedUpload.createdAt)
            dbUpload.modifiedAt should not be (dbUpdatedUpload.modifiedAt)
            dbUpdatedUpload.uploadStatus should be(UploadStatus.Processing)
            true
          }
      }
    }
  }

  test("delete a geojson upload") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            geojsonUploadCreate: GeojsonUpload.Create,
            projectCreate: Project.Create,
            projectLayerCreate: ProjectLayer.Create,
            annotationGroupCreate: AnnotationGroup.Create
        ) =>
          {
            val uploadInsertAndDeleteIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                projectCreate
              )
              dbLayer <- ProjectLayerDao.insertProjectLayer(
                projectLayerCreate
                  .copy(projectId = Some(dbProject.id))
                  .toProjectLayer
              )
              dbAnnotationGroup <- AnnotationGroupDao.createAnnotationGroup(
                dbProject.id,
                annotationGroupCreate,
                dbUser
              )
              dbGeojsonUpload <- GeojsonUploadDao.insert(
                geojsonUploadCreate,
                dbProject.id,
                dbLayer.id,
                dbAnnotationGroup.id,
                dbUser
              )
              _ <- GeojsonUploadDao.deleteProjectLayerUpload(
                dbProject.id,
                dbLayer.id,
                dbAnnotationGroup.id,
                dbGeojsonUpload.id
              )
              deletedUpload <- GeojsonUploadDao.getLayerUpload(
                dbProject.id,
                dbLayer.id,
                dbAnnotationGroup.id,
                dbGeojsonUpload.id)
            } yield deletedUpload

            val deletedUpload =
              uploadInsertAndDeleteIO.transact(xa).unsafeRunSync

            deletedUpload should be(None)
            true
          }
      }
    }
  }
}
