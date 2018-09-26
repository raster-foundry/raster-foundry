package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._

import cats.implicits._

import doobie._
import doobie.implicits._

import java.util.UUID

trait PropTestHelpers {

  def insertUserOrgPlatform(user: User.Create,
                            org: Organization.Create,
                            platform: Platform,
                            doUserGroupRole: Boolean = true)
    : ConnectionIO[(User, Organization, Platform)] =
    for {
      dbPlatform <- PlatformDao.create(platform)
      orgAndUser <- insertUserAndOrg(user,
                                     org.copy(platformId = dbPlatform.id),
                                     false)
      (dbOrg, dbUser) = orgAndUser
      _ <- if (doUserGroupRole)
        UserGroupRoleDao.create(
          UserGroupRole
            .Create(
              dbUser.id,
              GroupType.Platform,
              dbPlatform.id,
              GroupRole.Member
            )
            .toUserGroupRole(dbUser, MembershipStatus.Approved)
        )
      else ().pure[ConnectionIO]
    } yield { (dbUser, dbOrg, dbPlatform) }

  def insertUserOrgPlatProject(user: User.Create,
                               org: Organization.Create,
                               platform: Platform,
                               proj: Project.Create)
    : ConnectionIO[(User, Organization, Platform, Project)] =
    for {
      userOrgPlatform <- insertUserOrgPlatform(user, org, platform)
      (dbUser, dbOrg, dbPlatform) = userOrgPlatform
      dbProject <- ProjectDao.insertProject(fixupProjectCreate(dbUser, proj),
                                            dbUser)
    } yield (dbUser, dbOrg, dbPlatform, dbProject)

  def insertUserProject(user: User.Create,
                        org: Organization,
                        platform: Platform,
                        proj: Project.Create): ConnectionIO[(User, Project)] =
    for {
      dbUser <- UserDao.create(user)
      _ <- UserGroupRoleDao.create(
        UserGroupRole
          .Create(dbUser.id, GroupType.Organization, org.id, GroupRole.Member)
          .toUserGroupRole(dbUser, MembershipStatus.Approved))
      _ <- UserGroupRoleDao.create(
        UserGroupRole
          .Create(dbUser.id, GroupType.Platform, platform.id, GroupRole.Member)
          .toUserGroupRole(dbUser, MembershipStatus.Approved))
      dbProject <- ProjectDao.insertProject(fixupProjectCreate(dbUser, proj),
                                            dbUser)
    } yield (dbUser, dbProject)

  def insertUserAndOrg(
      user: User.Create,
      org: Organization.Create,
      doUserGroupRole: Boolean = true): ConnectionIO[(Organization, User)] = {
    for {
      orgInsert <- OrganizationDao.createOrganization(org)
      userInsert <- UserDao.create(user)
      _ <- if (doUserGroupRole)
        UserGroupRoleDao.create(
          UserGroupRole
            .Create(
              userInsert.id,
              GroupType.Organization,
              orgInsert.id,
              GroupRole.Member
            )
            .toUserGroupRole(userInsert, MembershipStatus.Approved))
      else ().pure[ConnectionIO]
    } yield (orgInsert, userInsert)
  }

  def insertUserOrgProject(
      user: User.Create,
      org: Organization.Create,
      proj: Project.Create): ConnectionIO[(Organization, User, Project)] = {
    for {
      orgUserInsert <- insertUserAndOrg(user, org)
      (org, user) = orgUserInsert
      project <- ProjectDao.insertProject(
        fixupProjectCreate(user, proj),
        user
      )
    } yield (org, user, project)
  }

  def insertUserOrgScene(user: User.Create,
                         org: Organization.Create,
                         scene: Scene.Create) = {
    for {
      orgUserInsert <- insertUserAndOrg(user, org)
      (dbOrg, dbUser) = orgUserInsert
      dbDatasource <- unsafeGetRandomDatasource
      scene <- SceneDao.insert(fixupSceneCreate(dbUser, dbDatasource, scene),
                               dbUser)
    } yield (dbOrg, dbUser, scene)
  }

  def unsafeGetRandomDatasource: ConnectionIO[Datasource] =
    (DatasourceDao.selectF ++ fr"limit 1").query[Datasource].unique

  def fixupProjectCreate(user: User, proj: Project.Create): Project.Create =
    proj.copy(owner = Some(user.id))

  // We assume the Scene.Create has an id, since otherwise thumbnails have no idea what scene id to use
  def fixupSceneCreate(user: User,
                       datasource: Datasource,
                       sceneCreate: Scene.Create): Scene.Create = {
    sceneCreate.copy(
      owner = Some(user.id),
      datasource = datasource.id,
      images = sceneCreate.images map {
        _.copy(scene = sceneCreate.id.get, owner = Some(user.id))
      },
      thumbnails = sceneCreate.thumbnails map {
        _.copy(sceneId = sceneCreate.id.get)
      },
      statusFields = sceneCreate.statusFields.copy(
        ingestStatus = sceneCreate.statusFields.ingestStatus match {
          case status @ (IngestStatus.Ingested | IngestStatus.Ingesting) =>
            status
          case _ => IngestStatus.NotIngested
        }
      )
    )
  }

  def fixupShapeCreate(user: User, shapeCreate: Shape.Create): Shape.Create =
    shapeCreate.copy(owner = Some(user.id))

  def fixupShapeGeoJSON(user: User,
                        shape: Shape,
                        shapeGeoJSON: Shape.GeoJSON): Shape.GeoJSON =
    shapeGeoJSON.copy(
      id = shape.id,
      properties = shapeGeoJSON.properties.copy(
        createdBy = user.id,
        modifiedBy = user.id,
        owner = user.id
      )
    )

  def fixupImageBanded(ownerId: String,
                       sceneId: UUID,
                       image: Image.Banded): Image.Banded =
    image.copy(owner = Some(ownerId), scene = sceneId)

  def fixupImage(ownerId: String, sceneId: UUID, image: Image): Image =
    image.copy(createdBy = ownerId, owner = ownerId, scene = sceneId)

  def fixupDatasource(dsCreate: Datasource.Create,
                      user: User): ConnectionIO[Datasource] = {
    for {
      ds <- DatasourceDao.createDatasource(dsCreate.copy(owner = Some(user.id)),
                                           user)
    } yield ds
  }

  def fixupThumbnail(scene: Scene.WithRelated,
                     thumbnail: Thumbnail): Thumbnail =
    thumbnail.copy(sceneId = scene.id)

  def fixupUploadCreate(user: User,
                        project: Project,
                        datasource: Datasource,
                        upload: Upload.Create): Upload.Create = {
    val withoutProjectFixup =
      upload.copy(owner = Some(user.id), datasource = datasource.id)
    upload.projectId match {
      case Some(_) => withoutProjectFixup.copy(projectId = Some(project.id))
      case _       => withoutProjectFixup
    }
  }

  def fixupAoiCreate(user: User,
                     project: Project,
                     aoiCreate: AOI.Create,
                     shape: Shape): AOI =
    aoiCreate
      .copy(owner = Some(user.id), shape = shape.id)
      .toAOI(project.id, user)

  def fixupAoi(user: User, aoi: AOI): AOI = {
    aoi.copy(owner = user.id, createdBy = user.id, modifiedBy = user.id)
  }

  def fixupTeam(teamCreate: Team.Create, org: Organization, user: User): Team =
    teamCreate.copy(organizationId = org.id).toTeam(user)

  def fixupUserGroupRole(
      user: User,
      organization: Organization,
      team: Team,
      platform: Platform,
      ugrCreate: UserGroupRole.Create): UserGroupRole.Create = {
    ugrCreate.groupType match {
      case GroupType.Platform =>
        ugrCreate.copy(groupId = platform.id, userId = user.id)
      case GroupType.Organization =>
        ugrCreate.copy(groupId = organization.id, userId = user.id)
      case GroupType.Team => ugrCreate.copy(groupId = team.id, userId = user.id)
    }
  }

  def fixTeamName(teamCreate: Team.Create, searchParams: SearchQueryParameters): Team.Create = searchParams.search match {
    case Some(teamName) if teamName.length != 0 => teamCreate.copy(name = teamName)
    case _ => teamCreate
  }

  def fixUpObjectAcr(
    acr: ObjectAccessControlRule,
    userTeamOrgPlat: (User, Team, Organization, Platform)
  ): ObjectAccessControlRule = {
    val (user, team, org, platform) = userTeamOrgPlat
    acr.subjectType match {
    case SubjectType.All => acr
    case SubjectType.Platform => acr.copy(subjectId = Some(platform.id.toString))
    case SubjectType.Organization => acr.copy(subjectId = Some(org.id.toString))
    case SubjectType.Team => acr.copy(subjectId = Some(team.id.toString))
    case SubjectType.User => acr.copy(subjectId = Some(user.id))
    }
  }

  def fixUpProjMiscInsert(
    userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
    project: Project.Create
  ): ConnectionIO[(Project, (User, Team, Organization, Platform))] = {
    val (user, team, org, platform) = userTeamOrgPlat
    for {
      dbUser <- UserDao.create(user)
      dbPlatform <- PlatformDao.create(platform)
      orgInsert <- OrganizationDao.createOrganization(org)
      dbOrg = orgInsert.copy(platformId = dbPlatform.id)
      dbTeam <- TeamDao.create(team.copy(organizationId = dbOrg.id).toTeam(dbUser))
      projectInsert <- ProjectDao.insertProject(fixupProjectCreate(dbUser, project), dbUser)
      dbUserTeamOrgPlat = (dbUser, dbTeam, dbOrg, dbPlatform)
    } yield { (projectInsert, dbUserTeamOrgPlat) }
  }
}
