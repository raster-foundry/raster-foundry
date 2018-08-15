package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.database.util.Page
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import geotrellis.vector.{Extent, Geometry, Polygon, Projected}
import java.util.UUID
import java.sql.{Date, Timestamp}

import com.lonelyplanet.akka.http.extensions.PageRequest
import scala.concurrent.Future

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.color._
import com.lonelyplanet.akka.http.extensions._
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.generic.JsonCodec
import io.circe.syntax._

import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._


object ProjectDao extends Dao[Project] {

  val tableName = "projects"

  val selectF = sql"""
    SELECT
      distinct(id), created_at, modified_at, created_by,
      modified_by, owner, name, slug_label, description,
      visibility, tile_visibility, is_aoi_project,
      aoi_cadence_millis, aois_last_checked, tags, extent,
      manual_order, is_single_band, single_band_options,
      default_annotation_group, extras
    FROM
  """ ++ tableF

  type SceneToProject = (UUID, UUID, Boolean, Option[Int], Option[Json])

  def unsafeGetProjectById(projectId: UUID): ConnectionIO[Project] = {
    val idFilter = Some(fr"id = ${projectId}")

    (selectF ++ Fragments.whereAndOpt(idFilter))
      .query[Project]
      .unique
  }

  def getProjectById(projectId: UUID, user: Option[User]): ConnectionIO[Option[Project]] = {
    val idFilter = Some(fr"id = ${projectId}")

    (selectF ++ Fragments.whereAndOpt(idFilter))
      .query[Project]
      .option
  }

  def isProjectPublic(projectId: UUID): ConnectionIO[Boolean] = {
    this.query
      .filter(projectId)
      .filter(fr"visibility = 'PUBLIC'")
      .exists
  }

  def insertProject(newProject: Project.Create, user: User): ConnectionIO[Project] = {
    val id = UUID.randomUUID()
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, newProject.owner)
    val slug = Project.slugify(newProject.name)
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, created_by,
        modified_by, owner, name, slug_label, description,
        visibility, tile_visibility, is_aoi_project,
        aoi_cadence_millis, aois_last_checked, tags, extent,
        manual_order, is_single_band, single_band_options, default_annotation_group,
        extras)
      VALUES
        ($id, $now, $now, ${user.id},
        ${user.id}, $ownerId, ${newProject.name}, $slug, ${newProject.description},
        ${newProject.visibility}, ${newProject.tileVisibility}, ${newProject.isAOIProject},
        ${newProject.aoiCadenceMillis}, $now, ${newProject.tags}, null,
        TRUE, ${newProject.isSingleBand}, ${newProject.singleBandOptions}, null,
        ${newProject.extras}
      )
    """).update.withUniqueGeneratedKeys[Project](
      "id", "created_at", "modified_at", "created_by",
      "modified_by", "owner", "name", "slug_label", "description",
      "visibility", "tile_visibility", "is_aoi_project",
      "aoi_cadence_millis", "aois_last_checked", "tags", "extent",
      "manual_order", "is_single_band", "single_band_options", "default_annotation_group",
      "extras"
    )
  }

  def updateProjectQ(project: Project, id: UUID, user: User): Update0 = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
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
       extras = ${project.extras}
    """ ++ Fragments.whereAndOpt(Some(idFilter))).update
    query
  }

  def updateProject(project: Project, id: UUID, user: User): ConnectionIO[Int] = {
    updateProjectQ(project, id, user).run
  }

  def deleteProject(id: UUID, user: User): ConnectionIO[Int] = {

    val aoiDeleteQuery = sql"DELETE FROM aois where aois.project_id = ${id}"
    for {
      _ <- aoiDeleteQuery.update.run
      projectDeleteCount <- query.filter(fr"id = ${id}").delete
    } yield projectDeleteCount
  }

  def updateSceneIngestStatus(projectId: UUID): ConnectionIO[Int] = {
    val updateStatusQuery =
      sql"""
           UPDATE scenes
           SET ingest_status = ${IngestStatus.ToBeIngested.toString} :: ingest_status
           FROM
             (SELECT scene_id
              FROM scenes
              INNER JOIN scenes_to_projects ON scene_id = scenes.id
              WHERE project_id = ${projectId}) sub
           WHERE (scenes.ingest_status = ${IngestStatus.NotIngested.toString} :: ingest_status OR
                  scenes.ingest_status = ${IngestStatus.Failed.toString} :: ingest_status )
           AND sub.scene_id = scenes.id
         """
    updateStatusQuery.update.run
  }

  def addScenesToProject(sceneIds: List[UUID], projectId: UUID, user: User, isAccepted: Boolean = true): ConnectionIO[Int] = {
    sceneIds.toNel match {
      case Some(ids) => addScenesToProject(ids, projectId, user, isAccepted)
      case _ => 0.pure[ConnectionIO]
    }
  }

  def addScenesToProject(sceneIds: NonEmptyList[UUID], projectId: UUID, user: User, isAccepted: Boolean): ConnectionIO[Int] = {
    val inClause = Fragments.in(fr"scenes.id",  sceneIds)
    val sceneIdWithDatasourceF = fr"""
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
       FROM scenes_to_projects
       WHERE project_id = ${projectId} AND accepted = true
      )
      AND """ ++ inClause
    for {
      sceneQueryResult <- sceneIdWithDatasourceF.query[(UUID, Datasource)].to[List]
      sceneToProjectInserts <- {
        val scenesToProject: List[SceneToProject] = sceneQueryResult.map { case (sceneId, datasource) =>
            createScenesToProject(sceneId, projectId, datasource, isAccepted)
        }
        val inserts = "INSERT INTO scenes_to_projects (scene_id, project_id, accepted, scene_order, mosaic_definition) VALUES (?, ?, ?, ?, ?)"
        Update[SceneToProject](inserts).updateMany(scenesToProject)
      }
      _ <- {sql"""
               UPDATE projects
               SET extent = subquery.extent
               FROM
                 (SELECT ST_SETSRID(ST_EXTENT(scenes.data_footprint), 3857) AS extent
                  FROM projects
                  INNER JOIN scenes_to_projects ON project_id = projects.id
                  INNER JOIN scenes ON scenes.id = scene_id
                  WHERE projects.id = ${projectId}
                  GROUP BY projects.id) AS subquery
               WHERE projects.id = ${projectId};
              """.update.run}
      _ <- updateSceneIngestStatus(projectId)
    } yield sceneToProjectInserts
  }

  def createScenesToProject(sceneId: UUID, projectId: UUID, datasource: Datasource, isAccepted: Boolean): SceneToProject = {
    val composites = datasource.composites
    val redBandPath = root.natural.selectDynamic("value").redBand.int
    val greenBandPath = root.natural.selectDynamic("value").greenBand.int
    val blueBandPath = root.natural.selectDynamic("value").blueBand.int

    val redBand = redBandPath.getOption(composites).getOrElse(0)
    val greenBand = greenBandPath.getOption(composites).getOrElse(1)
    val blueBand = blueBandPath.getOption(composites).getOrElse(2)
    (
      sceneId, projectId, isAccepted, None, Some(
        ColorCorrect.Params(
          redBand, greenBand, blueBand,             // Bands
          // Color corrections; everything starts out disabled (false) and null for now
          BandGamma(false, None, None, None),       // Gamma
          PerBandClipping(false, None, None, None,  // Clipping Max: R,G,B
            None, None, None), // Clipping Min: R,G,B
          MultiBandClipping(false, None, None),     // Min, Max
          SigmoidalContrast(false, None, None),     // Alpha, Beta
          Saturation(false, None),                  // Saturation
          Equalization(false),                      // Equalize
          AutoWhiteBalance(false)                    // Auto White Balance
        ).asJson
      )
    )
  }

  def listProjectSceneOrder(projectId: UUID, pr: PageRequest, user: User): ConnectionIO[PaginatedResponse[UUID]] = {
    val projectQuery = query.filter(projectId).select
    val selectIdF = fr"SELECT scene_id "
    val countIdF = fr"SELECT count(*) "
    val joinF = fr"FROM scenes_to_projects INNER JOIN scenes ON scene_id = scenes.id WHERE project_id = ${projectId}"
    val orderQ = projectQuery.map{ project =>
      project.manualOrder match {
        case true => joinF ++ fr"ORDER BY scene_order ASC, scene_id ASC"
        case _ => joinF ++ fr"ORDER BY acquisition_date ASC, cloud_cover ASC"
      }
    }

    for {
      ordered <- orderQ
      page <- (selectIdF ++ ordered ++ Page(pr)).query[UUID].to[List]
      count <- (countIdF ++ joinF).query[Int].unique
    } yield {
      val hasPrevious = pr.offset > 0
      val hasNext = (pr.offset * pr.limit) + 1 < count

      PaginatedResponse[UUID](count, hasPrevious, hasNext, pr.offset, pr.limit, page)
    }
  }

  def replaceScenesInProject(sceneIds: NonEmptyList[UUID], projectId: UUID, user: User): ConnectionIO[Iterable[Scene]] = {
    val deleteQuery = sql"DELETE FROM scenes_to_projects WHERE project_id = ${projectId}".update.run
    val scenesAdded = addScenesToProject(sceneIds, projectId, user, true)
    val projectScenes = SceneDao
      .query
      .filter(fr"scenes.id IN (SELECT scene_id FROM scenes_to_projects WHERE project_id = ${projectId}")
      .list

    for {
      _ <- deleteQuery
      _ <- scenesAdded
      scenes <- projectScenes
    } yield scenes
  }

  def deleteScenesFromProject(sceneIds: List[UUID], projectId: UUID): ConnectionIO[Int] = {
    val f:Option[Fragment] = sceneIds.toList.toNel.map(Fragments.in(fr"scene_id", _))
    val deleteQuery = fr"DELETE FROM scenes_to_projects" ++
      Fragments.whereAndOpt(f, Some(fr"project_id = ${projectId}"))
    deleteQuery.update.run
  }

  def addScenesToProjectFromQuery(sceneParams: CombinedSceneQueryParams, projectId: UUID, user: User): ConnectionIO[Int] = {

    for {
      scenes <- SceneDao.query.filter(sceneParams).list
      scenesAdded <- addScenesToProject(scenes.map(_.id), projectId, user)
    } yield scenesAdded
  }

  // head is safe here, because we're looking up users from the ids in projects, and the map was
  // build from those same ids.
  // throwing the exception is also safe, since the foreign key from project owners to users requires
  // that every project's owner is a key in the resulting list of users
  @SuppressWarnings(Array("TraversableHead"))
  def projectsToProjectsWithRelated(projectsPage: PaginatedResponse[Project]): ConnectionIO[PaginatedResponse[Project.WithUser]] =
    projectsPage.results.toList.toNel match {
      case Some(nelProjects) => {
        val usersIO: ConnectionIO[List[User]] =
          UserDao.query.filter(Fragments.in(fr"id", nelProjects map { _.owner })).list
        usersIO map {
          (users: List[User]) => {
            val groupedUsers = users.groupBy(_.id)
            val withUsers =
              projectsPage.results map {
                (project: Project) => Project.WithUser(
                  project,
                  groupedUsers.getOrElse(
                    project.owner,
                    throw new Exception("Somehow, a user id was lost to the aether")
                  ).head
                )
              }
            projectsPage.copy(results = withUsers)
          }
        }
      }
      case _ => {
        projectsPage.copy(results = List.empty[Project.WithUser]).pure[ConnectionIO]
      }
    }
}
