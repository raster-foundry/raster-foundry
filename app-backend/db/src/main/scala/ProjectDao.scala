package com.rasterfoundry.database

import com.rasterfoundry.common.{AWSBatch, S3}
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.color._
import com.rasterfoundry.datamodel.PageRequest
import cats.data._
import cats.implicits._
import cats.effect.{Async, IO, LiftIO}
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.vector.{Geometry, Projected}
import io.circe._
import io.circe.syntax._
import java.sql.Timestamp
import java.util.UUID
import java.net.URI
import java.net.URLDecoder

import com.rasterfoundry.database.util.Cache
import scalacache._
import scalacache.CatsEffect.modes._

import scala.concurrent.duration._

@SuppressWarnings(Array("EmptyCaseClass"))
final case class ProjectDao()

object ProjectDao
    extends Dao[Project]
    with AWSBatch
    with ObjectPermissions[Project] {

  val tableName = "projects"

  import Cache.ProjectCache._

  def deleteCache(id: UUID) = {
    val result = for {
      _ <- {
        val cacheKey = Project.cacheKey(id)
        logger.debug(s"Removing $cacheKey")
        remove(cacheKey)(projectCache, async[IO]).attempt
      }
    } yield ()
    Async[ConnectionIO].liftIO(result)
  }

  val selectF: Fragment = sql"""
    SELECT
      id, created_at, modified_at, created_by,
      owner, name, slug_label, description,
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
    cachingF(Project.cacheKey(projectId))(Some(30 minutes)) {
      projectByIdQuery(projectId).unique
    }

  def getProjectById(projectId: UUID): ConnectionIO[Option[Project]] = {
    Cache.getOptionCache(Project.cacheKey(projectId), Some(30 minutes)) {
      projectByIdQuery(projectId).option
    }
  }

  def listProjects(
      page: PageRequest,
      params: ProjectQueryParameters,
      user: User
  ): ConnectionIO[PaginatedResponse[Project.WithUser]] = {
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

  def insertProject(
      newProject: Project.Create,
      user: User
  ): ConnectionIO[Project] = {

    val id = UUID.randomUUID()
    val now = new Timestamp(new java.util.Date().getTime)
    val ownerId = util.Ownership.checkOwner(user, newProject.owner)
    val slug = Project.slugify(newProject.name)
    for {
      defaultProjectLayer <- ProjectLayerDao.insertProjectLayer(
        ProjectLayer(
          UUID.randomUUID(),
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
          None,
          None,
          None
        )
      )
      project <- (fr"INSERT INTO" ++ tableF ++ fr"""
          (id, created_at, modified_at, created_by,
          owner, name, slug_label, description,
          visibility, tile_visibility, is_aoi_project,
          aoi_cadence_millis, aois_last_checked, tags, extent,
          manual_order, is_single_band, single_band_options, default_annotation_group,
          extras, default_layer_id)
        VALUES
          ($id, $now, $now, ${user.id},
          $ownerId, ${newProject.name}, $slug, ${newProject.description},
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

  def updateProjectQ(project: Project, id: UUID): Update0 = {
    val updateTime = new Timestamp(new java.util.Date().getTime)
    val idFilter = fr"id = ${id}"

    val query = (fr"UPDATE" ++ tableF ++ fr"""SET
       modified_at = ${updateTime},
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

  def updateProject(
      project: Project,
      id: UUID
  ): ConnectionIO[Int] = {
    for {
      // User must have access to the project by the time they get here, so it exists
      dbProject <- unsafeGetProjectById(id)
      defaultLayer <- ProjectLayerDao.unsafeGetProjectLayerById(
        dbProject.defaultLayerId
      )
      _ <- ProjectLayerDao.updateProjectLayer(
        defaultLayer.copy(
          isSingleBand = project.isSingleBand,
          singleBandOptions = project.singleBandOptions
        ),
        dbProject.defaultLayerId
      )
      _ <- deleteCache(id)
      _ <- SceneToLayerDao.deleteMosaicDefCache(dbProject.defaultLayerId)
      updateProject <- updateProjectQ(project, id).run
    } yield updateProject
  }

  def deleteProject(
      id: UUID
  )(implicit L: LiftIO[ConnectionIO]): ConnectionIO[Int] = {
    val aoiDeleteQuery = sql"DELETE FROM aois where aois.project_id = ${id}"
    for {
      _ <- aoiDeleteQuery.update.run
      layers <- ProjectLayerDao.listProjectLayersForProjectQ(id).list
      _ <- layers
        .traverse(pl => {
          pl.overviewsLocation match {
            case Some(locUrl) => L.liftIO(removeLayerOverview(pl.id, locUrl))
            case _            => ().pure[ConnectionIO]
          }
        })
      projectDeleteCount <- query.filter(fr"id = ${id}").delete
      _ <- deleteCache(id)
    } yield {
      projectDeleteCount
    }
  }

  def getShareCount(projectId: UUID, userId: String): ConnectionIO[Long] = {
    val sharedUsers = ProjectDao.getPermissions(projectId).map { acrList =>
      acrList.flatten.filter(_.subjectType == SubjectType.User).flatMap { acr =>
        acr.subjectId
      }
    } map (subjects => subjects.filter(_ != userId))
    sharedUsers.map(_.length.toLong)
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
      projectLayerId: UUID,
      isAccepted: Boolean = true
  ): ConnectionIO[Int] = {
    sceneIds.toNel match {
      case Some(ids) =>
        addScenesToProject(ids, projectId, projectLayerId, isAccepted)
      case _ => 0.pure[ConnectionIO]
    }
  }

  def getFootprint(projectId: UUID): ConnectionIO[Option[Projected[Geometry]]] =
    for {
      projectO <- ProjectDao.getProjectById(projectId)
      footprint <- projectO traverse { project =>
        ProjectLayerScenesDao.getUnionedGeomFootprint(project.defaultLayerId)
      }
    } yield {
      footprint.flatten map { fp =>
        fp.reproject(CRS.fromEpsgCode(fp.srid), WebMercator)(3857)
      }
    }

  def updateProjectExtentIO(projectId: UUID): ConnectionIO[Int] = {
    val updateQueryIO =
      sql"""
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

    for {
      updateQuery <- updateQueryIO
      _ <- deleteCache(projectId)
    } yield updateQuery
  }

  def sceneIdWithDatasourceF(
      sceneIds: NonEmptyList[UUID],
      projectLayerId: UUID
  ): Fragment =
    fr"""
      SELECT scenes.id,
            datasources.id,
            datasources.created_at,
            datasources.created_by,
            datasources.modified_at,
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

  def addScenesToProject(
      sceneIds: NonEmptyList[UUID],
      projectId: UUID,
      projectLayerId: UUID,
      isAccepted: Boolean
  ): ConnectionIO[Int] = {
    for {
      project <- ProjectDao.unsafeGetProjectById(projectId)
      user <- UserDao.unsafeGetUserById(project.owner)
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
      _ <- updateProjectExtentIO(projectId)
      _ <- updateSceneIngestStatus(projectLayerId)
      scenesToIngest <- SceneWithRelatedDao.getScenesToIngest(projectLayerId)
      _ <- scenesToIngest traverse { (scene: Scene) =>
        logger.info(
          s"Kicking off ingest for scene ${scene.id} with ingest status ${scene.statusFields.ingestStatus}"
        )
        kickoffSceneIngest(scene.id).pure[ConnectionIO]
      }
      _ <- SceneToLayerDao.deleteMosaicDefCache(projectLayerId)
      _ <- scenesToIngest.traverse { (scene: Scene) =>
        SceneDao.update(
          scene.copy(
            statusFields =
              scene.statusFields.copy(ingestStatus = IngestStatus.ToBeIngested)
          ),
          scene.id,
          user
        )
      }
    } yield {
      logger
        .info(
          s"Kicking off layer overview creation for project-$projectId-layer-$projectLayerId"
        )
      sceneToLayerInserts
    }
  }

  def createScenesToLayer(
      sceneId: UUID,
      projectLayerId: UUID,
      datasource: Datasource,
      isAccepted: Boolean
  ): SceneToLayer = {
    val naturalComposites = datasource.composites.get("natural")
    val (redBand, greenBand, blueBand) = naturalComposites map { composite =>
      (
        composite.value.redBand,
        composite.value.greenBand,
        composite.value.blueBand
      )
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
            blueBand
          )
          .asJson
      )
    )
  }

  def replaceScenesInProject(
      sceneIds: NonEmptyList[UUID],
      projectId: UUID,
      projectLayerId: UUID
  ): ConnectionIO[Iterable[Scene]] =
    for {
      _ <- ProjectDao.unsafeGetProjectById(projectId)
      _ <- sql"DELETE FROM scenes_to_layers WHERE project_layer_id = ${projectLayerId}".update.run
      _ <- addScenesToProject(sceneIds, projectId, projectLayerId, true)
      scenes <- SceneDao.query
        .filter(
          fr"scenes.id IN (SELECT scene_id FROM scenes_to_layers WHERE project_layer_id = ${projectLayerId})"
        )
        .list
    } yield scenes

  def deleteScenesFromProject(
      sceneIds: List[UUID],
      projectId: UUID,
      projectLayerId: UUID
  )(implicit L: LiftIO[ConnectionIO]): ConnectionIO[Int] = {
    val f: Option[Fragment] = sceneIds.toNel.map(Fragments.in(fr"scene_id", _))
    f match {
      case _ @Some(_) =>
        for {
          _ <- ProjectDao.unsafeGetProjectById(projectId)
          rowsDeleted <- (fr"DELETE FROM scenes_to_layers" ++
            Fragments.whereAndOpt(
              f,
              Some(fr"project_layer_id = ${projectLayerId}")
            )).update.run
          _ <- updateProjectExtentIO(projectId)
          _ <- SceneToLayerDao.deleteMosaicDefCache(projectLayerId)
          layerDatasources <- ProjectLayerDatasourcesDao
            .listProjectLayerDatasources(projectLayerId)
          projectLayer <- ProjectLayerDao.unsafeGetProjectLayerById(
            projectLayerId
          )
          _ <- projectLayer.overviewsLocation match {
            case Some(locUrl) if layerDatasources.isEmpty =>
              (
                L.liftIO(removeLayerOverview(projectLayerId, locUrl)),
                ProjectLayerDao.updateProjectLayer(
                  projectLayer.copy(overviewsLocation = None),
                  projectLayer.id
                )
              ).tupled
            case _ => 0.pure[ConnectionIO]
          }
        } yield {
          rowsDeleted
        }
      case _ => 0.pure[ConnectionIO]
    }
  }

  // head is safe here, because we're looking up users from the ids in projects, and the map was
  // build from those same ids.
  // throwing the exception is also safe, since the foreign key from project owners to users requires
  // that every project's owner is a key in the resulting list of users
  @SuppressWarnings(Array("TraversableHead"))
  def projectsToProjectsWithRelated(
      projectsPage: PaginatedResponse[Project]
  ): ConnectionIO[PaginatedResponse[Project.WithUser]] =
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
                        "Somehow, a user id was lost to the aether"
                      )
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

  def authQuery(
      user: User,
      objectType: ObjectType,
      ownershipTypeO: Option[String] = None,
      groupTypeO: Option[GroupType] = None,
      groupIdO: Option[UUID] = None
  ): Dao.QueryBuilder[Project] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Project](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Project](
          selectF,
          tableF,
          List(
            queryObjectsF(
              user,
              objectType,
              ActionType.View,
              ownershipTypeO,
              groupTypeO,
              groupIdO
            )
          )
        )
    }

  def authorized(
      user: User,
      objectType: ObjectType,
      objectId: UUID,
      actionType: ActionType
  ): ConnectionIO[AuthResult[Project]] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .selectOption
      .map(AuthResult.fromOption _)

  def authProjectLayerExist(
      projectId: UUID,
      layerId: UUID,
      user: User,
      actionType: ActionType
  ): ConnectionIO[Boolean] =
    for {
      authProject <- authorized(user, ObjectType.Project, projectId, actionType)
      layerExist <- ProjectLayerDao.layerIsInProject(layerId, projectId)
    } yield {
      authProject.toBoolean && layerExist
    }

  def removeLayerOverview(projectLayerId: UUID, locUrl: String): IO[Unit] = {
    logger
      .info(
        s"No scenes left in project layer $projectLayerId with overview location $locUrl"
      )
    val jUri = URI.create(locUrl)
    val urlPath = jUri.getPath()
    val bucket = URLDecoder.decode(jUri.getHost(), "UTF-8")
    val key = URLDecoder.decode(urlPath.slice(1, urlPath.size), "UTF-8")
    val s3 = S3()
    IO { s3.doesObjectExist(bucket, key) } flatMap { exist =>
      if (exist) {
        logger
          .info(
            s"Found overview: $locUrl for layer $projectLayerId, deleting it..."
          )
        IO { s3.deleteObject(bucket, key) } map { _ =>
          logger
            .info(s"Deleted overview: $locUrl for layer $projectLayerId")
        }
      } else {
        IO {
          logger
            .info(s"Not Found overview: $locUrl for layer $projectLayerId")
        }
      }
    }
  }

  def getAnnotationProjectType(
      projectId: UUID
  ): ConnectionIO[Option[MLProjectType]] =
    for {
      projectO <- getProjectById(projectId)
      projectType = projectO match {
        case Some(project) =>
          project.tags.contains("annotate") match {
            case true =>
              project.extras match {
                case Some(extras) =>
                  extras.hcursor
                    .downField("annotate")
                    .get[String]("projectType")
                    .toOption
                case _ => Some("detection")
              }
            case _ => None
          }
        case _ => None
      }
    } yield { projectType.map(MLProjectType.fromString(_)) }

  def getAnnotationProjectStacInfo(
      projectId: UUID
  ): ConnectionIO[Option[StacLabelItemPropertiesThin]] = {
    val projectStacInfoF: Fragment = fr"""
    SELECT
      project_labels.name AS label_name,
      project_label_groups.name AS label_group_name
    FROM
    (
      SELECT labels.id, labels.name, labels."labelGroup" as label_group_id
      FROM
          projects,
          jsonb_to_recordset((projects.extras->'annotate')::jsonb ->'labels') AS  labels(id uuid, name text, "labelGroup" uuid)
      WHERE projects.id = ${projectId}
    ) as project_labels
    LEFT JOIN
    (
      SELECT labelGroups.key as id, labelGroups.value as name
      FROM
          projects,
          jsonb_each_text((projects.extras->'annotate')::jsonb ->'labelGroups') as labelGroups
      WHERE projects.id = ${projectId}
    ) as project_label_groups
    ON project_labels.label_group_id::uuid = project_label_groups.id::uuid;
    """
    projectStacInfoF
      .query[ProjectStacInfo]
      .to[List]
      .map(infoList => {
        infoList.length match {
          case 0 => None
          case _ =>
            val stacClasses
                : List[StacLabelItemProperties.StacLabelItemClasses] = (infoList
              .groupBy(_.labelGroupName match {
                case Some(groupName) if groupName.nonEmpty => groupName
                case _                                     => "label"
              })
              .map {
                case (propName, info) =>
                  StacLabelItemProperties.StacLabelItemClasses(
                    propName,
                    info.traverse(_.labelName).getOrElse(List())
                )
            } toList)
            val groupNamesDry = stacClasses.map(_.name).toSet
            val (property, taskType) =
              groupNamesDry.size match {
                case 1 => (List("label"), "detection")
                case _ => (groupNamesDry.toList, "classification")
              }
            Some(
              StacLabelItemPropertiesThin(
                property,
                stacClasses,
                "vector",
                taskType
              )
            )
        }
      })
  }

}
