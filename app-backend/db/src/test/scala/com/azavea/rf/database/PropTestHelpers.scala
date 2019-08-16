package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import io.circe.syntax._
import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec
case class ProjectExtras(annotate: ProjectExtrasAnnotate)

@JsonCodec
case class ProjectExtrasAnnotate(
    labelers: UUID,
    validators: UUID,
    labels: List[ProjectExtrasAnnotateLabel],
    projectType: String,
    preexistingTeams: Boolean,
    overlayUrl: Option[String] = None,
    labelGroups: Map[UUID, String]
)

@JsonCodec
case class ProjectExtrasAnnotateLabel(
    name: String,
    id: UUID,
    colorHexCode: String,
    labelGroup: UUID,
    default: Boolean
)

trait PropTestHelpers {

  def insertUserOrgPlatform(
      user: User.Create,
      org: Organization.Create,
      platform: Platform,
      doUserGroupRole: Boolean = true
  ): ConnectionIO[(User, Organization, Platform)] =
    for {
      dbPlatform <- PlatformDao.create(platform)
      orgAndUser <- insertUserAndOrg(
        user,
        org.copy(platformId = dbPlatform.id),
        false
      )
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

  def insertUserOrgPlatProject(
      user: User.Create,
      org: Organization.Create,
      platform: Platform,
      proj: Project.Create
  ): ConnectionIO[(User, Organization, Platform, Project)] =
    for {
      userOrgPlatform <- insertUserOrgPlatform(user, org, platform)
      (dbUser, dbOrg, dbPlatform) = userOrgPlatform
      dbProject <- ProjectDao.insertProject(
        fixupProjectCreate(dbUser, proj),
        dbUser
      )
    } yield (dbUser, dbOrg, dbPlatform, dbProject)

  def insertUserProject(
      user: User.Create,
      org: Organization,
      platform: Platform,
      proj: Project.Create
  ): ConnectionIO[(User, Project)] =
    for {
      dbUser <- UserDao.create(user)
      _ <- UserGroupRoleDao.create(
        UserGroupRole
          .Create(dbUser.id, GroupType.Organization, org.id, GroupRole.Member)
          .toUserGroupRole(dbUser, MembershipStatus.Approved)
      )
      _ <- UserGroupRoleDao.create(
        UserGroupRole
          .Create(dbUser.id, GroupType.Platform, platform.id, GroupRole.Member)
          .toUserGroupRole(dbUser, MembershipStatus.Approved)
      )
      dbProject <- ProjectDao.insertProject(
        fixupProjectCreate(dbUser, proj),
        dbUser
      )
    } yield (dbUser, dbProject)

  def insertUserAndOrg(
      user: User.Create,
      org: Organization.Create,
      doUserGroupRole: Boolean = true
  ): ConnectionIO[(Organization, User)] = {
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
            .toUserGroupRole(userInsert, MembershipStatus.Approved)
        )
      else ().pure[ConnectionIO]
    } yield (orgInsert, userInsert)
  }

  def insertUserOrgPlatScene(
      user: User.Create,
      org: Organization.Create,
      platform: Platform,
      scene: Scene.Create
  ) = {
    for {
      (dbUser, dbOrg, dbPlatform) <- insertUserOrgPlatform(user, org, platform)
      dbDatasource <- unsafeGetRandomDatasource
      scene <- SceneDao.insert(
        fixupSceneCreate(dbUser, dbDatasource, scene),
        dbUser
      )
    } yield (dbOrg, dbUser, dbPlatform, scene)
  }

  def unsafeGetRandomDatasource: ConnectionIO[Datasource] =
    (DatasourceDao.selectF ++ fr"limit 1").query[Datasource].unique

  def fixupProjectCreate(user: User, proj: Project.Create): Project.Create =
    proj.copy(owner = Some(user.id))

  // We assume the Scene.Create has an id, since otherwise thumbnails have no idea what scene id to use
  def fixupSceneCreate(
      user: User,
      datasource: Datasource,
      sceneCreate: Scene.Create
  ): Scene.Create = {
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

  def fixupShapeGeoJSON(
      user: User,
      shape: Shape,
      shapeGeoJSON: Shape.GeoJSON
  ): Shape.GeoJSON =
    shapeGeoJSON.copy(
      id = shape.id,
      properties = shapeGeoJSON.properties.copy(
        createdBy = user.id,
        owner = user.id
      )
    )

  def fixupImageBanded(
      ownerId: String,
      sceneId: UUID,
      image: Image.Banded
  ): Image.Banded =
    image.copy(owner = Some(ownerId), scene = sceneId)

  def fixupImage(ownerId: String, sceneId: UUID, image: Image): Image =
    image.copy(createdBy = ownerId, owner = ownerId, scene = sceneId)

  def fixupDatasource(
      dsCreate: Datasource.Create,
      user: User
  ): ConnectionIO[Datasource] =
    DatasourceDao.createDatasource(dsCreate.copy(owner = Some(user.id)), user)

  def fixupThumbnail(
      scene: Scene.WithRelated,
      thumbnail: Thumbnail
  ): Thumbnail =
    thumbnail.copy(sceneId = scene.id)

  def fixupUploadCreate(
      user: User,
      project: Project,
      datasource: Datasource,
      upload: Upload.Create
  ): Upload.Create = {
    val withoutProjectFixup =
      upload.copy(owner = Some(user.id), datasource = datasource.id)
    upload.projectId match {
      case Some(_) => withoutProjectFixup.copy(projectId = Some(project.id))
      case _       => withoutProjectFixup
    }
  }

  def fixupAoiCreate(
      user: User,
      project: Project,
      aoiCreate: AOI.Create,
      shape: Shape
  ): AOI =
    aoiCreate
      .copy(owner = Some(user.id), shape = shape.id)
      .toAOI(project.id, user)

  def fixupAoi(user: User, aoi: AOI): AOI = {
    aoi.copy(owner = user.id, createdBy = user.id)
  }

  def fixupTeam(teamCreate: Team.Create, org: Organization, user: User): Team =
    teamCreate.copy(organizationId = org.id).toTeam(user)

  def fixupUserGroupRole(
      user: User,
      organization: Organization,
      team: Team,
      platform: Platform,
      ugrCreate: UserGroupRole.Create
  ): UserGroupRole.Create = {
    ugrCreate.groupType match {
      case GroupType.Platform =>
        ugrCreate.copy(groupId = platform.id, userId = user.id)
      case GroupType.Organization =>
        ugrCreate.copy(groupId = organization.id, userId = user.id)
      case GroupType.Team => ugrCreate.copy(groupId = team.id, userId = user.id)
    }
  }

  def fixTeamName(
      teamCreate: Team.Create,
      searchParams: SearchQueryParameters
  ): Team.Create =
    searchParams.search match {
      case Some(teamName) if teamName.length != 0 =>
        teamCreate.copy(name = teamName)
      case _ => teamCreate
    }

  def fixUpObjectAcr(
      acr: ObjectAccessControlRule,
      userTeamOrgPlat: (User, Team, Organization, Platform)
  ): ObjectAccessControlRule = {
    val (user, team, org, platform) = userTeamOrgPlat
    acr.subjectType match {
      case SubjectType.All => acr
      case SubjectType.Platform =>
        acr.copy(subjectId = Some(platform.id.toString))
      case SubjectType.Organization =>
        acr.copy(subjectId = Some(org.id.toString))
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
      orgInsert <- OrganizationDao.createOrganization(
        org.copy(platformId = dbPlatform.id)
      )
      dbOrg = orgInsert.copy(platformId = dbPlatform.id)
      dbTeam <- TeamDao.create(
        team.copy(organizationId = dbOrg.id).toTeam(dbUser)
      )
      projectInsert <- ProjectDao.insertProject(
        fixupProjectCreate(dbUser, project),
        dbUser
      )
      dbUserTeamOrgPlat = (dbUser, dbTeam, dbOrg, dbPlatform)
    } yield { (projectInsert, dbUserTeamOrgPlat) }
  }

  def fixupMapToken(
      mapTokenCreate: MapToken.Create,
      user: User,
      project: Option[Project],
      analysis: Option[ToolRun]
  ): MapToken.Create =
    mapTokenCreate.copy(project = project map { _.id }, toolRun = analysis map {
      _.id
    }, owner = Some(user.id))

  def fixupTaskFeaturesCollection(
      tfc: Task.TaskFeatureCollectionCreate,
      project: Project,
      statusOption: Option[TaskStatus] = None
  ) =
    tfc.copy(
      features =
        tfc.features map { fixupTaskFeatureCreate(_, project, statusOption) }
    )

  def fixupTaskFeatureCreate(
      tfc: Task.TaskFeatureCreate,
      project: Project,
      statusOption: Option[TaskStatus] = None
  ): Task.TaskFeatureCreate =
    tfc.copy(
      properties =
        fixupTaskPropertiesCreate(tfc.properties, project, statusOption)
    )

  def fixupTaskPropertiesCreate(
      tpc: Task.TaskPropertiesCreate,
      project: Project,
      statusOption: Option[TaskStatus] = None
  ): Task.TaskPropertiesCreate =
    tpc.copy(
      projectId = project.id,
      projectLayerId = project.defaultLayerId,
      status = statusOption.getOrElse(tpc.status)
    )

  def fixupProjectExtrasUpdate(
      labelValidateTeamCreate: (Team.Create, Team.Create),
      labelValidateTeamUgrCreate: (UserGroupRole.Create, UserGroupRole.Create),
      dbOrg: Organization,
      dbUser: User,
      dbPlatform: Platform,
      dbProject: Project,
      labelsOption: Option[List[(UUID, String, UUID)]] = None,
      labelGroupsOption: Option[Map[UUID, String]] = None
  ): ConnectionIO[Project] = {
    val (labelTeamCreate, validateTeamCreate) = labelValidateTeamCreate
    val (labelTeamUgrCreate, validateTeamUgrCreate) = labelValidateTeamUgrCreate
    for {
      dbLabelTeam <- TeamDao.create(
        labelTeamCreate
          .copy(organizationId = dbOrg.id)
          .toTeam(dbUser)
      )
      dbValidateTeam <- TeamDao.create(
        validateTeamCreate
          .copy(organizationId = dbOrg.id)
          .toTeam(dbUser)
      )
      _ <- UserGroupRoleDao.create(
        fixupUserGroupRole(
          dbUser,
          dbOrg,
          dbLabelTeam,
          dbPlatform,
          labelTeamUgrCreate.copy(groupType = GroupType.Team)
        ).toUserGroupRole(dbUser, MembershipStatus.Approved)
      )
      _ <- UserGroupRoleDao.create(
        fixupUserGroupRole(
          dbUser,
          dbOrg,
          dbValidateTeam,
          dbPlatform,
          validateTeamUgrCreate.copy(groupType = GroupType.Team)
        ).toUserGroupRole(dbUser, MembershipStatus.Approved)
      )
      _ <- ProjectDao.updateProject(
        dbProject.copy(
          extras = Some(
            fixupProjectExtrasAnnotate(
              dbLabelTeam.id,
              dbValidateTeam.id,
              labelsOption,
              labelGroupsOption
            ).asJson
          )
        ),
        dbProject.id
      )
      updatedDbProject <- ProjectDao.unsafeGetProjectById(dbProject.id)
    } yield updatedDbProject
  }

  def fixupProjectExtrasAnnotate(
      labelTeamId: UUID,
      validateTeamId: UUID,
      labelsOption: Option[List[(UUID, String, UUID)]] = None,
      labelGroupsOption: Option[Map[UUID, String]] = None
  ): ProjectExtras = (labelsOption, labelGroupsOption) match {
    case (Some(labels), Some(labelGroups)) =>
      val createdLabels = labels.map(label => {
        ProjectExtrasAnnotateLabel(
          label._2,
          label._1,
          "red",
          label._3,
          false
        )
      })
      ProjectExtras(
        ProjectExtrasAnnotate(
          labelTeamId,
          validateTeamId,
          createdLabels,
          "detection",
          true,
          None,
          labelGroups
        ))
    case _ =>
      val defaultLabelId = UUID.randomUUID()
      val defaultLayerGroupId = UUID.randomUUID()
      val defaultLabels = List(
        ProjectExtrasAnnotateLabel(
          "Test",
          defaultLabelId,
          "red",
          defaultLayerGroupId,
          false
        )
      )
      ProjectExtras(
        ProjectExtrasAnnotate(
          labelTeamId,
          validateTeamId,
          defaultLabels,
          "detection",
          true,
          None,
          Map(defaultLayerGroupId -> "Test Group")
        ))
  }

  def fixupStacExportCreate(
      stacExportCreate: StacExport.Create,
      user: User,
      project: Project
  ): StacExport.Create =
    stacExportCreate.copy(
      layerDefinitions = List(
        StacExport.LayerDefinition(project.id, project.defaultLayerId)
      ),
      owner = Some(user.id)
    )
}
