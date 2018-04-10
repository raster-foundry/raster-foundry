package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._

import doobie._
import doobie.implicits._

import java.util.UUID

trait PropTestHelpers {

  def insertUserAndOrg(user: User.Create, org: Organization.Create): ConnectionIO[(Organization, User)] = {
    for {
      orgInsert <- OrganizationDao.createOrganization(org)
      userInsert <- UserDao.create(user.copy(organizationId = orgInsert.id))
    } yield (orgInsert, userInsert)
  }

  def insertUserOrgProject(user: User.Create, org: Organization.Create, proj: Project.Create):
      ConnectionIO[(Organization, User, Project)] = {
    for {
      orgUserInsert <- insertUserAndOrg(user, org)
      (org, user) = orgUserInsert
      project <- ProjectDao.insertProject(
        fixupProjectCreate(user, org, proj), user
      )
    } yield (org, user, project)
  }

  def unsafeGetRandomDatasource: ConnectionIO[Datasource] =
    (DatasourceDao.selectF ++ fr"ORDER BY RANDOM() limit 1").query[Datasource].unique

  def fixupProjectCreate(user: User, org: Organization, proj: Project.Create): Project.Create = {
    proj.copy(organizationId = org.id, owner = Some(user.id))
  }

  // We assume the Scene.Create has an id, since otherwise thumbnails have no idea what scene id to use
  def fixupSceneCreate(user: User, org: Organization, datasource: Datasource, sceneCreate: Scene.Create): Scene.Create = {
    sceneCreate.copy(
      organizationId = org.id,
      owner = None,
      datasource = datasource.id,
      images = sceneCreate.images map {
        _.copy(organizationId = org.id, scene = sceneCreate.id.get, owner = None)
      },
      thumbnails = sceneCreate.thumbnails map {
        _.copy(organizationId = org.id, sceneId = sceneCreate.id.get)
      }
    )
  }

  def fixupImageBanded(ownerId: String, orgId: UUID, sceneId: UUID, image: Image.Banded): Image.Banded = {
    image.copy(
      owner = Some(ownerId),
      organizationId = orgId,
      scene = sceneId
    )
  }

  def fixupImage(ownerId: String, orgId: UUID, sceneId: UUID, image: Image): Image = {
    image.copy(
      createdBy = ownerId,
      owner = ownerId,
      organizationId = orgId,
      scene = sceneId
    )
  }

}
