package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class DatasourceDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("inserting a datasource") {
    check {
      forAll(
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            dsCreate: Datasource.Create
        ) => {
          val createDsIO = for {
            (userInsert, _, _) <- insertUserOrgPlatform(
              userCreate,
              orgCreate,
              platform
            )
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
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            dsCreate: Datasource.Create
        ) => {
          val getDsIO = for {
            (userInsert, _, _) <- insertUserOrgPlatform(
              userCreate,
              orgCreate,
              platform
            )
            dsInsert <- fixupDatasource(dsCreate, userInsert)
            dsGet <- DatasourceDao.getDatasourceById(dsInsert.id)
          } yield dsGet
          val getDs = getDsIO.transact(xa).unsafeRunSync
          getDs.get.name === dsCreate.name
        }
      )
    }
  }

  test("getting a datasource for a scene") {
    check {
      forAll(
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            datasourceCreate: Datasource.Create,
            scene: Scene.Create
        ) => {
          val dsFetch = for {
            (userInsert, _, _) <- insertUserOrgPlatform(
              userCreate,
              orgCreate,
              platform
            )
            dsInsert <- fixupDatasource(datasourceCreate, userInsert)
            sceneInsert <- SceneDao
              .insert(fixupSceneCreate(userInsert, dsInsert, scene), userInsert)
            fetched <- DatasourceDao.getSceneDatasource(sceneInsert.id)
          } yield { (fetched, dsInsert) }

          val (fetched, inserted) = dsFetch.transact(xa).unsafeRunSync
          assert(
            fetched.get == inserted,
            "Fetched datasource did not equal inserted datasource"
          )
          true
        }
      )
    }
  }

  test("getting a datasource by id unsafely") {
    check {
      forAll(
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            dsCreate: Datasource.Create
        ) => {
          val getDsUnsafeIO = for {
            (userInsert, _, _) <- insertUserOrgPlatform(
              userCreate,
              orgCreate,
              platform
            )
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
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            dsCreate: Datasource.Create,
            dsUpdate: Datasource.Create
        ) => {
          val updateDsIO = for {
            (userInsert, _, _) <- insertUserOrgPlatform(
              userCreate,
              orgCreate,
              platform
            )
            dsInsert <- fixupDatasource(dsCreate, userInsert)
            dsUpdated <- fixupDatasource(dsUpdate, userInsert)
            rowUpdated <- DatasourceDao
              .updateDatasource(dsUpdated, dsInsert.id)
          } yield (rowUpdated, dsUpdated)
          val (rowUpdated, dsUpdated) =
            updateDsIO.transact(xa).unsafeRunSync
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
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            dsCreate: Datasource.Create
        ) => {
          val deleteDsIO = for {
            (userInsert, _, _) <- insertUserOrgPlatform(
              userCreate,
              orgCreate,
              platform
            )
            dsInsert <- fixupDatasource(dsCreate, userInsert)
            rowDeleted <- DatasourceDao.deleteDatasource(dsInsert.id)
          } yield rowDeleted
          val deleteDsRowCount =
            deleteDsIO.transact(xa).unsafeRunSync
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
        (
            userCreate: User.Create,
            ownerCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            dsCreate: Datasource.Create
        ) => {
          val isDsDeletableIO = for {
            (ownerInsert, _, _) <- insertUserOrgPlatform(
              ownerCreate,
              orgCreate,
              platform
            )
            userInsert <- UserDao.create(userCreate)
            dsInsert <- fixupDatasource(dsCreate, ownerInsert)
            isDeletableUser <- DatasourceDao
              .isDeletable(dsInsert.id, userInsert)
            isDeletableOwner <- DatasourceDao
              .isDeletable(dsInsert.id, ownerInsert)
          } yield { (isDeletableUser, isDeletableOwner) }

          val (isDeletableUser, isDeletableOwner) =
            isDsDeletableIO.transact(xa).unsafeRunSync

          assert(
            !isDeletableUser,
            "Non-owner of a datasource should not be able to delete a datasource"
          )
          assert(
            isDeletableOwner,
            "Owner of a datasource should be able to delete a datasource"
          )
          true
        }
      )
    }
  }

  test("isDeletable should return false if a datasource is shared") {
    check {
      forAll(
        (
            ownerCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            dsCreate: Datasource.Create
        ) => {
          val isDsDeletableIO = for {
            (ownerInsert, _, _) <- insertUserOrgPlatform(
              ownerCreate,
              orgCreate,
              platform
            )
            dsInsert <- fixupDatasource(dsCreate, ownerInsert)
            _ <- DatasourceDao.addPermission(
              dsInsert.id,
              ObjectAccessControlRule(SubjectType.All, None, ActionType.View)
            )
            isDeletable <- DatasourceDao.isDeletable(dsInsert.id, ownerInsert)
          } yield { isDeletable }

          val isDeletable =
            isDsDeletableIO.transact(xa).unsafeRunSync
          assert(
            !isDeletable,
            "isDeletable should return false if a datasource is shared"
          )
          true
        }
      )
    }
  }

  test(
    "isDeletable should return false if a datasource has an upload not completed/failed/aborted"
  ) {
    check {
      forAll(
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            dsCreate: Datasource.Create,
            project: Project.Create,
            upload: Upload.Create
        ) => {
          @SuppressWarnings(Array("TraversableHead"))
          val isDsDeletableIO = for {
            (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
              userCreate,
              orgCreate,
              platform,
              project
            )
            datasource <- fixupDatasource(dsCreate, dbUser)
            _ <- UploadDao.insert(
              fixupUploadCreate(dbUser, dbProject, datasource, upload),
              dbUser,
              0
            )
            isDeletable <- DatasourceDao.isDeletable(datasource.id, dbUser)
          } yield { (isDeletable, upload.uploadStatus) }

          val (isDeletable, uploadStatus) =
            isDsDeletableIO.transact(xa).unsafeRunSync
          val ok = List(
            UploadStatus.Created,
            UploadStatus.Uploading,
            UploadStatus.Uploaded,
            UploadStatus.Queued,
            UploadStatus.Processing
          ).contains(uploadStatus) != isDeletable
          assert(
            ok,
            "isDeletable should return false if a datasource has an upload not in completed/failed/aborted"
          )
          true
        }
      )
    }
  }

}
