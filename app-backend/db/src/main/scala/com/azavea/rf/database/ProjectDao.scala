package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import geotrellis.slick.Projected
import geotrellis.vector.Polygon
import java.util.UUID
import java.sql.{Date, Timestamp}

import com.lonelyplanet.akka.http.extensions.PageRequest
import scala.concurrent.Future

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.color._
import com.lonelyplanet.akka.http.extensions._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, Geometry}
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
      id, created_at, modified_at, organization_id, created_by,
      modified_by, owner, name, slug_label, description,
      visibility, tile_visibility, is_aoi_project,
      aoi_cadence_millis, aois_last_checked, tags, extent,
      manual_order, is_single_band, single_band_options
    FROM
  """ ++ tableF

  def insertProject(newProject: Project.Create, user: User): ConnectionIO[Project] = {
    val id = UUID.randomUUID()
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, newProject.owner)
    val slug = Project.slugify(newProject.name)
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, organization_id, created_by,
        modified_by, owner, name, slug_label, description,
        visibility, tile_visibility, is_aoi_project,
        aoi_cadence_millis, aois_last_checked, tags, extent,
        manual_order, is_single_band, single_band_options)
      VALUES
        ($id, $now, $now, ${newProject.organizationId}, ${user.id},
        ${user.id}, $ownerId, ${newProject.name}, $slug, ${newProject.description},
        ${newProject.visibility}, ${newProject.tileVisibility}, ${newProject.isAOIProject},
        ${newProject.aoiCadenceMillis}, $now, ${newProject.tags}, null,
        TRUE, ${newProject.isSingleBand}, ${newProject.singleBandOptions})
    """).update.withUniqueGeneratedKeys[Project](
      "id", "created_at", "modified_at", "organization_id", "created_by",
      "modified_by", "owner", "name", "slug_label", "description",
      "visibility", "tile_visibility", "is_aoi_project",
      "aoi_cadence_millis", "aois_last_checked", "tags", "extent",
      "manual_order", "is_single_band", "single_band_options"
    )
  }

  def filterUserVisibility(user: User) = {
    user.isInRootOrganization match {
      case true => None
      case _ => Some(fr"(organization_id = ${user.organizationId} OR owner = ${user.id} OR visibility = 'PUBLIC')")
    }
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
       single_band_options = ${project.singleBandOptions}
    """ ++ Fragments.whereAndOpt(ownerEditFilter(user), Some(idFilter))).update
    query
  }

  def updateProject(project: Project, id: UUID, user: User): ConnectionIO[Int] = {
    updateProjectQ(project, id, user).run
  }

  def deleteProject(id: UUID, user: User): ConnectionIO[Int] = {

    val aoiDeleteQuery = sql"DELETE FROM aois USING aois_to_projects WHERE aois.id = aois_to_projects.aoi_id AND aois_to_projects.project_id = ${id}"
    val aoiToProjectDelete = sql"DELETE FROM aois_to_projects WHERE project_id = ${id}"
    for {
      _ <- aoiDeleteQuery.update.run
      _ <- aoiToProjectDelete.update.run
      projectDeleteCount <- query.filter(fr"id = ${id}").delete
    } yield projectDeleteCount
  }
//
//  def listAOIs(projectId: UUID, page: PageRequest, user: User): Future[PaginatedResponse[Project]] = ???
//
//  def listProjectScenes(projectId: UUID, page: PageRequest, sceneParams: CombinedSceneQueryParams, user: User): Future[PaginatedResponse[Scene]] = ???

  type stp = (UUID, UUID, Boolean, Option[Int], Option[Json])

  def updateSceneIngestStatus(projectId: UUID): ConnectionIO[Int] = {
    val updateStatusQuery =
      sql"""
           UPDATE scenes
           SET ingest_status = ${IngestStatus.ToBeIngested} :: ingest_status
           FROM
             (SELECT scene_id
              FROM scenes
              INNER JOIN scenes_to_projects ON scene_id = scenes.id
              WHERE project_id = ${projectId}) sub
           WHERE (scenes.ingest_status = ${IngestStatus.NotIngested.toString} OR
                  scenes.ingest_stuatus = ${IngestStatus.Failed.toString})
           AND sub.scene_id = scenes.id
         """
    updateStatusQuery.update.run
  }

  def addScenesToProject(sceneIds: List[UUID], projectId: UUID, user: User): ConnectionIO[Int] = {
    sceneIds.toNel match {
      case Some(ids) => addScenesToProject(ids, projectId, user)
      case _ => 0.pure[ConnectionIO]
    }
  }

  def addScenesToProject(sceneIds: NonEmptyList[UUID], projectId: UUID, user: User): ConnectionIO[Int] = {
    val inClause = Fragments.in(fr"scenes.id", sceneIds)
    val x = sql"""
         SELECT scenes.id,
                datasources.id,
                datasources.created_at,
                datasources.created_by,
                datasources.modified_at,
                datasources.modified_by,
                datasources.owner,
                datasources.organization_id,
                datasources.name,
                datasources.visibility,
                datasources.composites,
                datasources.extras,
                datasources.bands
         FROM scenes
         INNER JOIN datasources ON scenes.datasource = datasources.id
         WHERE NOT (scenes.id = ANY(ARRAY(SELECT scene_id FROM scenes_to_projects WHERE project_id = ${projectId})::UUID[])) AND """ ++ inClause
    for {
      sceneQueryResult <- x.query[(UUID, Datasource)].list
      sceneToProjectInserts <- {
        val stps: List[stp] = sceneQueryResult.map { case (sceneId, datasource) =>
            createScenesToProject(sceneId, projectId, datasource)
        }
        val inserts = "INSERT INTO scenes_to_projects (scene_id, project_id, accepted, scene_order, mosaic_definition) VALUES (?, ?, ?, ?, ?)"
        Update[stp](inserts).updateMany(stps)
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

  def createScenesToProject(sceneId: UUID, projectId: UUID, datasource: Datasource): stp = {
    val composites = datasource.composites
    val redBandPath = root.natural.selectDynamic("value").redBand.int
    val greenBandPath = root.natural.selectDynamic("value").greenBand.int
    val blueBandPath = root.natural.selectDynamic("value").blueBand.int

    val redBand = redBandPath.getOption(composites).getOrElse(0)
    val greenBand = greenBandPath.getOption(composites).getOrElse(1)
    val blueBand = blueBandPath.getOption(composites).getOrElse(2)

    (
      sceneId, projectId, true, None, Some(
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

  def listProjectSceneOrder(projectId: UUID, page: PageRequest, user: User): ConnectionIO[PaginatedResponse[UUID]] = {
    val projectQuery = query.filter(fr"id = ${projectId}").filter(ownerEditFilter(user)).select
    val selectClause = fr"SELECT scene_id "
    val countClause = fr"SELECT count(*) "
    val sceneQuery = fr"FROM scenes_to_projects INNER JOIN scenes ON scene_id = scenes.id WHERE project_id = ${projectId}"
    val orderFragment = projectQuery.map{ project =>
      project.manualOrder match {
        case true => sceneQuery ++ fr"ORDER BY scene_order DESC, scene_id ASC"
        case _ => sceneQuery ++ fr"ORDER BY acquisition_date DESC, cloud_cover ASC"
      }
    }
    for {
      orderedQuery <- orderFragment
      pageResponse <- (selectClause ++ orderedQuery ++ fr"LIMIT ${page.limit} OFFSET ${page.offset * page.limit}").query[UUID].list
      countResponse <- (countClause ++ sceneQuery).query[Int].unique
    } yield {
      val hasPrevious = page.offset > 0
      val hasNext = (page.offset * page.limit) + 1 < countResponse
      PaginatedResponse[UUID](countResponse, hasPrevious, hasNext, page.offset, page.limit, pageResponse)
    }
  }

  def replaceScenesInProject(sceneIds: NonEmptyList[UUID], projectId: UUID, user: User): ConnectionIO[Iterable[Scene]] = {
    val deleteQuery = sql"DELETE FROM scenes_to_projects WHERE project_id = ${projectId}".update.run
    val scenesAdded = addScenesToProject(sceneIds, projectId, user)
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
    val f:Option[Fragment] = sceneIds.toNel.map(Fragments.in(fr"scene_id", _))
    val deleteQuery = sql"DELETE FROM scenes_to_projects" ++ Fragments.whereAndOpt(f) ++ fr"project_id = ${projectId}"
    deleteQuery.update.run
  }

  def addScenesToProjectFromQuery(sceneParams: CombinedSceneQueryParams, projectId: UUID, user: User): ConnectionIO[Int] = {

    for {
      scenes <- SceneDao.query.filter(sceneParams).list
      scenesAdded <- addScenesToProject(scenes.map(_.id), projectId, user)
    } yield scenesAdded

  }

  def create(
    user: User,
    owner: Option[String],
    organizationId: UUID,
    name: String,
    description: String,
    visibility: Visibility,
    tileVisibility: Visibility,
    isAOIProject: Boolean = false,
    aoiCadenceMillis: Long = Project.DEFAULT_CADENCE,
    tags: List[String],
    isSingleBand: Boolean = false,
    singleBandOptions: Option[SingleBandOptions.Params]
  ): ConnectionIO[Project] = {
    val id = UUID.randomUUID()
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, owner)
    val slug = Project.slugify(name)
    val userId = user.id
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, organization_id, created_by,
        modified_by, owner, name, slug_label, description,
        visibility, tile_visibility, is_aoi_project,
        aoi_cadence_millis, aois_last_checked, tags, extent,
        manual_order, is_single_band, single_band_options)
      VALUES
        ($id, $now, $now, $organizationId, $userId,
        $userId, $ownerId, $name, $slug, $description,
        $visibility, $tileVisibility, $isAOIProject,
        $aoiCadenceMillis, $now, $tags, null,
        TRUE, $isSingleBand, $singleBandOptions)
    """).update.withUniqueGeneratedKeys[Project](
      "id", "created_at", "modified_at", "organization_id", "created_by",
      "modified_by", "owner", "name", "slug_label", "description",
      "visibility", "tile_visibility", "is_aoi_project",
      "aoi_cadence_millis", "aois_last_checked", "tags", "extent",
      "manual_order", "is_single_band", "single_band_options"
    )
  }
}