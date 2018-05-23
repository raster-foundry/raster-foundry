package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._

import cats.implicits._

import doobie._
import doobie.implicits._

import java.util.UUID

trait PropTestHelpers {

  def insertUserOrgPlatform(user: User.Create, org: Organization.Create, platform: Platform, doUserGroupRole: Boolean = true):
      ConnectionIO[(User, Organization, Platform)] = for {
      dbPlatform <- PlatformDao.create(platform)
      orgAndUser <- insertUserAndOrg(user, org.copy(platformId=dbPlatform.id), false)
      (dbOrg, dbUser) = orgAndUser
      _ <- if (doUserGroupRole) UserGroupRoleDao.create(
        UserGroupRole.Create(
          dbUser.id,
          GroupType.Platform,
          dbPlatform.id,
          GroupRole.Member
        ).toUserGroupRole(dbUser)
      ) else ().pure[ConnectionIO]
    } yield { (dbUser, dbOrg, dbPlatform) }

  def insertUserAndOrg(user: User.Create, org: Organization.Create, doUserGroupRole: Boolean = true):
      ConnectionIO[(Organization, User)] = {
    for {
      orgInsert <- OrganizationDao.createOrganization(org)
      userInsert <- UserDao.create(user)
      _ <- if (doUserGroupRole) UserGroupRoleDao.create(
        UserGroupRole.Create(
          userInsert.id,
          GroupType.Organization,
          orgInsert.id,
          GroupRole.Member
        ).toUserGroupRole(userInsert)) else ().pure[ConnectionIO]
    } yield (orgInsert, userInsert)
  }

  def insertUserOrgProject(user: User.Create, org: Organization.Create, proj: Project.Create):
      ConnectionIO[(Organization, User, Project)] = {
    for {
      orgUserInsert <- insertUserAndOrg(user, org)
      (org, user) = orgUserInsert
      project <- ProjectDao.insertProject(
        fixupProjectCreate(user, proj), user
      )
    } yield (org, user, project)
  }

  def insertUserOrgScene(user: User.Create, org: Organization.Create, scene: Scene.Create) = {
    for {
      orgUserInsert <- insertUserAndOrg(user, org)
      (dbOrg, dbUser) = orgUserInsert
      dbDatasource <- unsafeGetRandomDatasource
      scene <- SceneDao.insert(fixupSceneCreate(dbUser, dbDatasource, scene), dbUser)
    } yield (dbOrg, dbUser, scene)
  }

  def unsafeGetRandomDatasource: ConnectionIO[Datasource] =
    (DatasourceDao.selectF ++ fr"ORDER BY RANDOM() limit 1").query[Datasource].unique

  def fixupProjectCreate(user: User, proj: Project.Create): Project.Create =
    proj.copy(owner = Some(user.id))

  // We assume the Scene.Create has an id, since otherwise thumbnails have no idea what scene id to use
  def fixupSceneCreate(user: User, datasource: Datasource, sceneCreate: Scene.Create): Scene.Create = {
    sceneCreate.copy(
      owner = None,
      datasource = datasource.id,
      images = sceneCreate.images map {
        _.copy(scene = sceneCreate.id.get, owner = None)
      },
      thumbnails = sceneCreate.thumbnails map {
        _.copy(sceneId = sceneCreate.id.get)
      }
    )
  }

  def fixupShapeCreate(user: User, shapeCreate: Shape.Create): Shape.Create =
    shapeCreate.copy(owner = Some(user.id))

  def fixupShapeGeoJSON(user: User, shape: Shape, shapeGeoJSON: Shape.GeoJSON): Shape.GeoJSON =
    shapeGeoJSON.copy(
      id = shape.id,
      properties = shapeGeoJSON.properties.copy(
        createdBy = user.id,
        modifiedBy = user.id,
        owner = user.id
      )
    )

  def fixupImageBanded(ownerId: String, sceneId: UUID, image: Image.Banded): Image.Banded =
    image.copy(owner = Some(ownerId), scene = sceneId)

  def fixupImage(ownerId: String, sceneId: UUID, image: Image): Image =
    image.copy(createdBy = ownerId, owner = ownerId, scene = sceneId)

  def fixupDatasource(dsCreate: Datasource.Create, user: User): ConnectionIO[Datasource] = {
    for {
      ds <- DatasourceDao.createDatasource(dsCreate.copy(owner = Some(user.id)), user)
    } yield ds
  }

  def fixupThumbnail(scene: Scene.WithRelated, thumbnail: Thumbnail): Thumbnail =
    thumbnail.copy(sceneId = scene.id)

  def fixupUploadCreate(user: User, project: Project, datasource: Datasource, upload: Upload.Create): Upload.Create = {
    val withoutProjectFixup = upload.copy(owner = Some(user.id), datasource = datasource.id)
    upload.projectId match {
      case Some(_) => withoutProjectFixup.copy(projectId = Some(project.id))
      case _ => withoutProjectFixup
    }
  }

  def fixupAoi(user: User, aoi: AOI): AOI = {
    aoi.copy(owner = user.id, createdBy = user.id, modifiedBy = user.id)
  }

  def fixupTeam(teamCreate: Team.Create, org: Organization, user: User): Team =
    teamCreate.copy(organizationId = org.id).toTeam(user)

  def fixupUserGroupRole(user: User, organization: Organization, team: Team, platform: Platform, ugrCreate: UserGroupRole.Create): UserGroupRole.Create = {
    ugrCreate.groupType match {
      case GroupType.Platform => ugrCreate.copy(groupId = platform.id, userId = user.id)
      case GroupType.Organization => ugrCreate.copy(groupId = organization.id, userId = user.id)
      case GroupType.Team => ugrCreate.copy(groupId = team.id, userId = user.id)
    }
  }
}
