package com.azavea.rf.database

import com.azavea.rf.datamodel._

import doobie.ConnectionIO

trait PropTestHelpers {

  def insertUserAndOrg(user: User.Create, org: Organization.Create): ConnectionIO[(Organization, User)] = {
    for {
      orgInsert <- OrganizationDao.createOrganization(org)
      userInsert <- UserDao.create(user.copy(organizationId = orgInsert.id))
    } yield (orgInsert, userInsert)
  }

  // We assume the Scene.Create has an id, since otherwise thumbnails have no idea what scene id to use
  def fixupSceneCreate(user: User, org: Organization, sceneCreate: Scene.Create): Scene.Create = {
    sceneCreate.copy(
      organizationId = org.id,
      owner = Some(user.id),
      images = sceneCreate.images map {
        _.copy(organizationId = org.id, scene = sceneCreate.id.get, owner = Some(user.id))
      },
      thumbnails = sceneCreate.thumbnails map {
        _.copy(organizationId = org.id, sceneId = sceneCreate.id.get)
      }
    )
  }

}
