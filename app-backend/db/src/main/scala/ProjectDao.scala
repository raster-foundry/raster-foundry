package com.rasterfoundry.database

import com.rasterfoundry.common.AWSBatch
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.color._

import com.lonelyplanet.akka.http.extensions.PageRequest
import cats.data._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import io.circe._
import io.circe.syntax._

import java.sql.Timestamp
import java.util.UUID

object ProjectDao
    extends Dao[Project]
    with AWSBatch
    with ObjectPermissions[Project] {

  val tableName = "projects"

  val selectF: Fragment = sql"""
    SELECT
      id, created_at, modified_at, created_by,
      modified_by, owner, name, slug_label, description,
      visibility, tile_visibility, is_aoi_project,
      aoi_cadence_millis, aois_last_checked, tags, extent,
      manual_order, is_single_band, single_band_options,
      default_annotation_group, extras, default_layer_id
    FROM
  """ ++ tableF

  type SceneToProject = (UUID, UUID, Boolean, Option[Int], Option[Json])
  type SceneToLayer = (UUID, UUID, Boolean, Option[Int], Option[Json])

  def projectByIdQuery(projectId: UUID): Query0[Project] = {
    val idFilter = Some(fr"id = ${projectId}")

    (selectF ++ Fragments.whereAndOpt(idFilter))
      .query[Project]
  }

  def unsafeGetProjectById(projectId: UUID): ConnectionIO[Project] =
    projectByIdQuery(projectId).unique

  def getProjectById(projectId: UUID): ConnectionIO[Option[Project]] =
    projectByIdQuery(projectId).option

  def listProjects(
      page: PageRequest,
      params: ProjectQueryParameters,
      user: User): ConnectionIO[PaginatedResponse[Project.WithUser]] = {
    authQuery(
      user,
      ObjectType.Project,
      params.ownershipTypeParams.ownershipType,
      params.groupQueryParameters.groupType,
      params.groupQueryParameters.groupId
    ).filter(params)
      .page(page)
      .flatMap(projectsToProjectsWithRelated)
  }

  def isProjectPublic(projectId: UUID): ConnectionIO[Boolean] = {
    this.query
      .filter(projectId)
      .filter(fr"visibility = 'PUBLIC'")
      .exists
  }

  def insertProject(newProject: Project.Create,
                    user: User): ConnectionIO[Project] = {
    val id = UUID.randomUUID()
    val now = new Timestamp(new java.util.Date().getTime)
    val ownerId = util.Ownership.checkOwner(user, newProject.owner)
    val slug = Project.slugify(newProject.name)
    for {
      defaultProjectLayer <- ProjectLayerDao.insertProjectLayer(
        ProjectLayer(UUID.randomUUID(),
                     now,
                     now,
                     "Project default layer",
                     None,
                     "#738FFC",
                     None,
                     None,
                     None,
                     None,
                     false,
                     None)
      )
      project <- (fr"INSERT INTO" ++ tableF ++ fr"""
          (id, created_at, modified_at, created_by,
          modified_by, owner, name, slug_label, description,
          visibility, tile_visibility, is_aoi_project,
          aoi_cadence_millis, aois_last_checked, tags, extent,
          manual_order, is_single_band, single_band_options, default_annotation_group,
          extras, default_layer_id)
        VALUES
          ($id, $now, $now, ${user.id},
          ${user.id}, $ownerId, ${newProject.name}, $slug, ${newProject.description},
          ${newProject.visibility}, ${newProject.tileVisibility}, ${newProject.isAOIProject},
          ${newProject.aoiCadenceMillis}, $now, ${newProject.tags}, null,
          TRUE, ${newProject.isSingleBand}, ${newProject.singleBandOptions}, null,
          ${newProject.extras}, ${defaultProjectLayer.id}
        )
      """).update.withUniqueGeneratedKeys[Project](
        "id",
        "created_at",
        "modified_at",
        "created_by",
        "modified_by",
        "owner",
        "name",
        "slug_label",
        "description",
        "visibility",
        "tile_visibility",
        "is_aoi_project",
        "aoi_cadence_millis",
        "aois_last_checked",
        "tags",
        "extent",
        "manual_order",
        "is_single_band",
        "single_band_options",
        "default_annotation_group",
        "extras",
        "default_layer_id"
      )
      updatedLayer = defaultProjectLayer.copy(projectId = Some(project.id))
      _ <- ProjectLayerDao.updateProjectLayer(updatedLayer, updatedLayer.id)
    } yield project
  }

  def updateProjectQ(project: Project, id: UUID, user: User): Update0 = {
    val updateTime = new Timestamp(new java.util.Date().getTime)
    val idFilter = fr"id = ${id}"

    val query = (fr"UPDATE" ++ tableF ++ fr"""SET
       modified_at = ${updateTime},
       modified_by = ${user.id},
       owner = ${project.owner},
       name = ${project.name},
       description = ${project.description},
       visibility = ${project.visibility},
       tile_visibility = ${project.tileVisibility},
       is_aoi_project = ${project.isAOIProject},
       aoi_cadence_millis = ${project.aoiCadenceMillis},
       aois_last_checked = ${project.aoisLastChecked},
       tags = ${project.tags},
       extent = ${project.extent},
       manual_order = ${project.manualOrder},
       is_single_band = ${project.isSingleBand},
       single_band_options = ${project.singleBandOptions},
       default_annotation_group = ${project.defaultAnnotationGroup},
       extras = ${project.extras},
       default_layer_id = ${project.defaultLayerId}
    """ ++ Fragments.whereAndOpt(Some(idFilter))).update
    query
  }

  def updateProject(project: Project,
                    id: UUID,
                    user: User): ConnectionIO[Int] = {
    for {
      // User must have access to the project by the time they get here, so it exists
      dbProject <- unsafeGetProjectById(id)
      updateSceneOrder <- (project.manualOrder, dbProject.manualOrder) match {
        case (true, false) =>
          SceneToLayerDao.addSceneOrdering(id)
        case _ =>
          0.pure[ConnectionIO]
      }
      defaultLayer <- ProjectLayerDao.unsafeGetProjectLayerById(
        dbProject.defaultLayerId)
      _ <- ProjectLayerDao.updateProjectLayer(
        defaultLayer.copy(isSingleBand = project.isSingleBand,
                          singleBandOptions = project.singleBandOptions),
        dbProject.defaultLayerId
      )
      updateProject <- updateProjectQ(project, id, user).run
    } yield updateProject
  }

  def deleteProject(id: UUID): ConnectionIO[Int] = {

    val aoiDeleteQuery = sql"DELETE FROM aois where aois.project_id = ${id}"
    for {
      _ <- aoiDeleteQuery.update.run
      projectDeleteCount <- query.filter(fr"id = ${id}").delete
    } yield projectDeleteCount
  }

  def updateSceneIngestStatus(projectLayerId: UUID): ConnectionIO[Int] = {
    val updateStatusQuery =
      sql"""
           UPDATE scenes
           SET ingest_status = ${IngestStatus.Queued.toString} :: ingest_status
           FROM
             (SELECT scene_id
              FROM scenes
              INNER JOIN scenes_to_layers ON scene_id = scenes.id
              WHERE project_layer_id = ${projectLayerId}) sub
           WHERE (scenes.ingest_status = ${IngestStatus.NotIngested.toString} :: ingest_status OR
                  scenes.ingest_status = ${IngestStatus.Failed.toString} :: ingest_status OR
                  (scenes.ingest_status = ${IngestStatus.Ingesting.toString} :: ingest_status AND
                   (now() - modified_at) > '1 day'::interval))
           AND sub.scene_id = scenes.id
           AND (scene_type = 'AVRO' :: scene_type OR scene_type IS NULL)
         """
    updateStatusQuery.update.run
  }

  def addScenesToProject(
      sceneIds: List[UUID],
      projectId: UUID,
      isAccepted: Boolean = true,
      projectLayerIdO: Option[UUID] = None): ConnectionIO[Int] = {
    sceneIds.toNel match {
      case Some(ids) =>
        addScenesToProject(ids, projectId, isAccepted, projectLayerIdO)
      case _ => 0.pure[ConnectionIO]
    }
  }

  def updateProjectExtentIO(projectId: UUID): ConnectionIO[Int] = sql"""
    UPDATE projects
    SET extent = subquery.extent
    FROM
     (SELECT ST_SETSRID(ST_EXTENT(s.data_footprint), 3857) AS extent
      FROM projects p
      INNER JOIN project_layers pl ON pl.project_id = p.id
      INNER JOIN scenes_to_layers stl ON stl.project_layer_id = pl.id
      INNER JOIN scenes s ON s.id = stl.scene_id
      WHERE p.id = ${projectId}
      GROUP BY p.id) AS subquery
    WHERE projects.id = ${projectId};
    """.update.run

  def sceneIdWithDatasourceF(sceneIds: NonEmptyList[UUID],
                             projectLayerId: UUID): Fragment =
    fr"""
      SELECT scenes.id,
            datasources.id,
            datasources.created_at,
            datasources.created_by,
            datasources.modified_at,
            datasources.modified_by,
            datasources.owner,
            datasources.name,
            datasources.visibility,
            datasources.composites,
            datasources.extras,
            datasources.bands,
            datasources.license_name
      FROM scenes
      INNER JOIN datasources ON scenes.datasource = datasources.id
      WHERE
      scenes.id NOT IN (
       SELECT scene_id
       FROM scenes_to_layers
       WHERE project_layer_id = ${projectLayerId} AND accepted = true
      ) AND """ ++ Fragments.in(fr"scenes.id", sceneIds)

  def getProjectLayerId(projectLayerIdO: Option[UUID], project: Project): UUID =
    (projectLayerIdO, project.defaultLayerId) match {
      case (Some(projectLayerId), _) => projectLayerId
      case (_, defaultLayerId)       => defaultLayerId
      case _ =>
        throw new Exception(s"Project ${project.id} does not have any layers")
    }

  def addScenesToProject(sceneIds: NonEmptyList[UUID],
                         projectId: UUID,
                         isAccepted: Boolean,
                         projectLayerIdO: Option[UUID]): ConnectionIO[Int] = {
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      user <- UserDao.unsafeGetUserById(project.owner)
      projectLayerId = getProjectLayerId(projectLayerIdO, project)
      sceneIdWithDatasource <- sceneIdWithDatasourceF(sceneIds, projectLayerId)
        .query[(UUID, Datasource)]
        .to[List]
      sceneToLayerInserts <- {
        val scenesToLayer: List[SceneToLayer] = sceneIdWithDatasource.map {
          case (sceneId, datasource) =>
            createScenesToLayer(sceneId, projectLayerId, datasource, isAccepted)
        }
        val insertScenesToLayers =
          "INSERT INTO scenes_to_layers (scene_id, project_layer_id, accepted, scene_order, mosaic_definition) VALUES (?, ?, ?, ?, ?)"
        Update[SceneToLayer](insertScenesToLayers).updateMany(scenesToLayer)
      }
      // TODO: delete this when we can get rid of scenes_to_projects entirely
      _ <- {
        val scenesToProject: List[SceneToProject] = sceneIdWithDatasource.map {
          case (sceneId, datasource) =>
            createScenesToProject(sceneId, projectId, datasource, isAccepted)
        }
        val insertScenesToProjects =
          "INSERT INTO scenes_to_projects (scene_id, project_id, accepted, scene_order, mosaic_definition) VALUES (?, ?, ?, ?, ?)"
        Update[SceneToProject](insertScenesToProjects)
          .updateMany(scenesToProject)
      }
      _ <- updateProjectExtentIO(projectId)
      _ <- updateSceneIngestStatus(projectLayerId)
      scenesToIngest <- SceneWithRelatedDao.getScenesToIngest(projectLayerId)
      _ <- scenesToIngest traverse { (scene: Scene) =>
        logger.info(
          s"Kicking off ingest for scene ${scene.id} with ingest status ${scene.statusFields.ingestStatus}")
        kickoffSceneIngest(scene.id).pure[ConnectionIO]
      }
      _ <- scenesToIngest.traverse { (scene: Scene) =>
        SceneDao.update(
          scene.copy(statusFields =
            scene.statusFields.copy(ingestStatus = IngestStatus.ToBeIngested)),
          scene.id,
          user
        )
      }
    } yield sceneToLayerInserts
  }

  def createScenesToLayer(sceneId: UUID,
                          projectLayerId: UUID,
                          datasource: Datasource,
                          isAccepted: Boolean): SceneToLayer = {
    val naturalComposites = datasource.composites.get("natural")
    val (redBand, greenBand, blueBand) = naturalComposites map { composite =>
      (composite.value.redBand,
       composite.value.greenBand,
       composite.value.blueBand)
    } getOrElse { (0, 1, 2) }
    (
      sceneId,
      projectLayerId,
      isAccepted,
      None,
      Some(
        ColorCorrect
          .Params(
            redBand,
            greenBand,
            blueBand, // Bands
            // Color corrections; everything starts out disabled (false) and null for now
            BandGamma(enabled = false, None, None, None), // Gamma
            PerBandClipping(enabled = false,
                            None,
                            None,
                            None, // Clipping Max: R,G,B
                            None,
                            None,
                            None), // Clipping Min: R,G,B
            MultiBandClipping(enabled = false, None, None), // Min, Max
            SigmoidalContrast(enabled = false, None, None), // Alpha, Beta
            Saturation(enabled = false, None), // Saturation
            Equalization(false), // Equalize
            AutoWhiteBalance(false) // Auto White Balance
          )
          .asJson
      )
    )
  }

  // TODO: delete this function when we can get rid of scenes_to_projects entirely
  def createScenesToProject(sceneId: UUID,
                            projectId: UUID,
                            datasource: Datasource,
                            isAccepted: Boolean): SceneToProject = {
    val naturalComposites = datasource.composites.get("natural")
    val (redBand, greenBand, blueBand) = naturalComposites map { composite =>
      (composite.value.redBand,
       composite.value.greenBand,
       composite.value.blueBand)
    } getOrElse { (0, 1, 2) }
    (
      sceneId,
      projectId,
      isAccepted,
      None,
      Some(
        ColorCorrect
          .Params(
            redBand,
            greenBand,
            blueBand, // Bands
            // Color corrections; everything starts out disabled (false) and null for now
            BandGamma(enabled = false, None, None, None), // Gamma
            PerBandClipping(enabled = false,
                            None,
                            None,
                            None, // Clipping Max: R,G,B
                            None,
                            None,
                            None), // Clipping Min: R,G,B
            MultiBandClipping(enabled = false, None, None), // Min, Max
            SigmoidalContrast(enabled = false, None, None), // Alpha, Beta
            Saturation(enabled = false, None), // Saturation
            Equalization(false), // Equalize
            AutoWhiteBalance(false) // Auto White Balance
          )
          .asJson
      )
    )
  }

  def replaceScenesInProject(
      sceneIds: NonEmptyList[UUID],
      projectId: UUID,
      projectLayerIdO: Option[UUID] = None): ConnectionIO[Iterable[Scene]] =
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      projectLayerId = getProjectLayerId(projectLayerIdO, project)
      // TODO: delete below one line when we are ready to remove scenes_to_projects table
      _ <- sql"DELETE FROM scenes_to_projects WHERE project_id = ${projectId}".update.run
      _ <- sql"DELETE FROM scenes_to_layers WHERE project_layer_id = ${projectLayerId}".update.run
      _ <- addScenesToProject(sceneIds,
                              projectId,
                              isAccepted = true,
                              projectLayerIdO)
      scenes <- SceneDao.query
        .filter(
          fr"scenes.id IN (SELECT scene_id FROM scenes_to_layers WHERE project_layer_id = ${projectLayerId})")
        .list
    } yield scenes

  def deleteScenesFromProject(
      sceneIds: List[UUID],
      projectId: UUID,
      projectLayerIdO: Option[UUID] = None): ConnectionIO[Int] = {
    val f: Option[Fragment] = sceneIds.toNel.map(Fragments.in(fr"scene_id", _))
    f match {
      case fragO @ Some(_) =>
        for {
          project <- ProjectDao.unsafeGetProjectById(projectId)
          projectLayerId = getProjectLayerId(projectLayerIdO, project)
          // TODO: delete below line when we are ready to remove scenes_to_projects table
          _ <- (fr"DELETE FROM scenes_to_projects" ++
            Fragments.whereAndOpt(f, Some(fr"project_id = ${projectId}"))).update.run
          rowsDeleted <- (fr"DELETE FROM scenes_to_layers" ++
            Fragments.whereAndOpt(
              f,
              Some(fr"project_layer_id = ${projectLayerId}"))).update.run
          _ <- updateProjectExtentIO(projectId)
        } yield rowsDeleted
      case _ => 0.pure[ConnectionIO]
    }
  }

  // head is safe here, because we're looking up users from the ids in projects, and the map was
  // build from those same ids.
  // throwing the exception is also safe, since the foreign key from project owners to users requires
  // that every project's owner is a key in the resulting list of users
  @SuppressWarnings(Array("TraversableHead"))
  def projectsToProjectsWithRelated(projectsPage: PaginatedResponse[Project])
    : ConnectionIO[PaginatedResponse[Project.WithUser]] =
    projectsPage.results.toList.toNel match {
      case Some(nelProjects) =>
        val usersIO: ConnectionIO[List[User]] =
          UserDao.query
            .filter(Fragments.in(fr"id", nelProjects map { _.owner }))
            .list
        usersIO map { users: List[User] =>
          {
            val groupedUsers = users.groupBy(_.id)
            val withUsers =
              projectsPage.results map { project: Project =>
                Project.WithUser(
                  project,
                  groupedUsers
                    .getOrElse(
                      project.owner,
                      throw new Exception(
                        "Somehow, a user id was lost to the aether")
                    )
                    .head
                    .withScrubbedName
                )
              }
            projectsPage.copy(results = withUsers)
          }
        }
      case _ =>
        projectsPage
          .copy(results = List.empty[Project.WithUser])
          .pure[ConnectionIO]
    }

  def authQuery(user: User,
                objectType: ObjectType,
                ownershipTypeO: Option[String] = None,
                groupTypeO: Option[GroupType] = None,
                groupIdO: Option[UUID] = None): Dao.QueryBuilder[Project] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Project](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Project](selectF,
                                  tableF,
                                  List(
                                    queryObjectsF(user,
                                                  objectType,
                                                  ActionType.View,
                                                  ownershipTypeO,
                                                  groupTypeO,
                                                  groupIdO)))
    }

  def authorized(user: User,
                 objectType: ObjectType,
                 objectId: UUID,
                 actionType: ActionType): ConnectionIO[Boolean] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .exists

  def authProjectLayerExist(projectId: UUID,
                            layerId: UUID,
                            user: User,
                            actionType: ActionType): ConnectionIO[Boolean] =
    for {
      authProject <- authorized(user, ObjectType.Project, projectId, actionType)
      layerExist <- ProjectLayerDao.layerIsInProject(layerId, projectId)
    } yield { authProject && layerExist }
}
