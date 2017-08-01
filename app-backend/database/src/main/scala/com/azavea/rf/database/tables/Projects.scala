package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.{Date, UUID}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.http.scaladsl.model.{IllegalRequestException, StatusCodes}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields._
import com.azavea.rf.database.query._
import com.azavea.rf.database.sort._
import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.color._
import com.lonelyplanet.akka.http.extensions._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, Geometry}
import io.circe._
import io.circe.optics.JsonPath._

/** Table description of table projects. Objects of this class serve as prototypes for rows in queries. */
class Projects(_tableTag: Tag) extends Table[Project](_tableTag, "projects")
                                      with ProjectFields
                                      with OrganizationFkFields
                                      with UserFkVisibleFields
                                      with TimestampFields
{
  def * = (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, owner, name,
    slugLabel, description, visibility, tileVisibility, isAOIProject, aoiCadenceMillis,
    aoisLastChecked, tags, extent, manualOrder, isSingleBand, singleBandOptions) <> (Project.tupled, Project.unapply)

  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val organizationId: Rep[UUID] = column[UUID]("organization_id")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val name: Rep[String] = column[String]("name")
  val slugLabel: Rep[String] = column[String]("slug_label", O.Length(255,varying=true))
  val description: Rep[String] = column[String]("description")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val tileVisibility: Rep[Visibility] = column[Visibility]("tile_visibility")
  val tags: Rep[List[String]] = column[List[String]]("tags", O.Length(2147483647,varying=false), O.Default(List.empty))
  val extent: Rep[Option[Projected[Geometry]]] = column[Option[Projected[Geometry]]]("extent", O.Length(2147483647,varying=false), O.Default(None))
  val manualOrder: Rep[Boolean] = column[Boolean]("manual_order", O.Default(true))
  val isAOIProject: Rep[Boolean] = column[Boolean]("is_aoi_project", O.Default(false))
  val aoiCadenceMillis: Rep[Long] = column[Long]("aoi_cadence_millis")
  val aoisLastChecked: Rep[Timestamp] = column[Timestamp]("aois_last_checked")
  val isSingleBand: Rep[Boolean] = column[Boolean]("is_single_band", O.Default(false))
  val singleBandOptions: Rep[Option[SingleBandOptions.Params]] = column[Option[SingleBandOptions.Params]]("single_band_options")

  lazy val organizationsFk = foreignKey("projects_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("projects_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("projects_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val ownerUserFK = foreignKey("projects_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object Projects extends TableQuery(tag => new Projects(tag)) with LazyLogging {
  type TableQuery = Query[Projects, Project, Seq]

  implicit val projectsSorter: QuerySorter[Projects] =
    new QuerySorter(
      new ProjectFieldsSort(identity[Projects]),
      new OrganizationFkSort(identity[Projects]),
      new VisibilitySort(identity[Projects]),
      new TimestampSort(identity[Projects]))


  implicit class withProjectsTableQuery[M, U, C[_]](projects: TableQuery) {
    def page(pageRequest: PageRequest): TableQuery = {
      val sorted = projects.sort(pageRequest.sort)
      sorted.drop(pageRequest.offset * pageRequest.limit).take(pageRequest.limit)
    }
  }

  /** Add project to database
    *
    * @param project Project project to add to database
    */
  def insertProject(project: Project)
                  (implicit database: DB): Future[Project] = {

    database.db.run {
      Projects.forceInsert(project)
    } map { _ =>
      project
    }
  }

  /** Get the AOIs belonging to a project. */
  def listAOIs(
    projectId: UUID,
    pageRequest: PageRequest,
    user: User
  )(implicit database: DB): Future[PaginatedResponse[AOI]] = {
    val aois: Future[Seq[UUID]] = database.db.run {
      AoisToProjects
        .filter(_.projectId === projectId)
        .map(_.aoiId)
        .result
    }

    aois.flatMap({ ids =>
      val aoisQ: Query[AOIs, AOI, Seq] =
        AOIs.filterToSharedOrganizationIfNotInRoot(user).filter(_.id inSet ids)

      val paginated: Future[Seq[AOI]] = database.db.run(AOIs.page(pageRequest, aoisQ).result)
      val totalQ: Future[Int] = database.db.run(aoisQ.length.result)

      for {
        total <- totalQ
        aois  <- paginated
      } yield {
        val hasNext = (pageRequest.offset + 1) * pageRequest.limit < total
        val hasPrevious = pageRequest.offset > 0

        PaginatedResponse[AOI](
          total, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, aois
        )
      }
    })
  }

  /** Get scenes belonging to a project
    *
    * @param projectId Project for which associated Scenes should be returned
    * @param pageRequest PageRequest denoting the page of results
    * @param combinedParams CombinedSceneQueryParams filtering the request
    * @param user User making the request
    */
  def listProjectScenes(projectId: UUID, pageRequest: PageRequest, combinedParams: CombinedSceneQueryParams, user: User)
    (implicit database: DB): Future[PaginatedResponse[Scene.WithRelated]] = {

    val injectedParams = combinedParams.copy(
      sceneParams=combinedParams.sceneParams.copy(project=Some(projectId))
    )

    Scenes.listScenes(pageRequest, injectedParams, user)
  }

  def listProjectSceneOrder(projectId: UUID, pageRequest: PageRequest, user: User)
    (implicit database: DB): Future[PaginatedResponse[UUID]] =
      database.db.run {
        Projects
          .filterUserVisibility(user)
          .filter(_.id === projectId)
          .map(_.manualOrder)
          .result
      }.flatMap { p =>
        p.headOption match {
          case Some(true) =>
            ScenesToProjects.listManualOrder(projectId, pageRequest)
          case _ =>
            val pageReq = pageRequest.copy(sort = Map("acquisitionDate" -> Order.Desc, "cloudCover" -> Order.Asc))
            Projects
              .listProjectScenes(projectId, pageReq, CombinedSceneQueryParams(), user)
              .map { page =>
                page.copy(results = page.results.map(_.id))
              }
        }
      }


  /** Get specific scenes from a project. Returns any scenes from the list of
    * specified ids that are associated to the given project.
    *
    * @param projectId UUID primary key of the project to limit results to
    * @param sceneIds Seq[UUID] primary key of scenes to retrieve
    */
  def listSelectProjectScenes(projectId: UUID, sceneIds: Seq[UUID])
    (implicit database: DB): Future[Iterable[Scene.WithRelated]] = {

    val scenes = for {
      sceneToProject <- ScenesToProjects if sceneToProject.projectId === projectId && sceneToProject.sceneId.inSet(sceneIds)
      scene <- Scenes if scene.id === sceneToProject.sceneId
    } yield scene

    database.db.run {
      scenes.joinWithRelated.result
    }.map(Scene.WithRelated.fromRecords)
  }

  /** Get public project given a projectId
    *
    * @param projectId UUID primary key of project to retrieve
    */
  def getPublicProject(projectId: UUID)
                      (implicit database: DB): Future[Option[Project]] = {

    database.db.run {
      Projects
        .filter(_.tileVisibility === Visibility.fromString("PUBLIC"))
        .filter(_.id === projectId)
        .result
        .headOption
    }
  }

  /** Get project given a projectId and user
    *
    * @param projectId UUID primary key of project to retrieve
    * @param user      Results will be limited to user's organization
    */
  def getProject(projectId: UUID, user: User)
                (implicit database: DB): Future[Option[Project]] = {

    database.db.run {
      Projects
        .filterToSharedOrganizationIfNotInRoot(user)
        .filter(_.id === projectId)
        .result
        .headOption
    }
  }

  /** Get AOI project and its AOI given a projectId and user
    *
    * @param projectId UUID primary key of project to retrieve
    * @param user      Results will be limited to user's organization
    */
  def getAOIProject(projectId: UUID, user: User)
                   (implicit database: DB): Future[Option[(Project, AOI)]] = {

    database.db.run {
      Projects
        .filterToSharedOrganizationIfNotInRoot(user)
        .filter(_.id === projectId)
        .join(AoisToProjects)
        .on { case (p, o) => p.id === o.projectId }
        .join(AOIs)
        .on { case ((_, atp), a) => atp.aoiId === a.id }
        .map { case ((p, _), a) => p -> a }
        .result
        .headOption
    }
  }

  /** List projects after applying filters and sorting
    *
    * @param pageRequest PageRequest pagination parameters
    * @param queryParams ProjectQueryParams query parameters relevant for projects
    * @param user User making the request
    */
  def listProjects(pageRequest: PageRequest, queryParams: ProjectQueryParameters, user: User)
                 (implicit database: DB): Future[PaginatedResponse[Project]] = {

    val projects = Projects.filterUserVisibility(user)
      .filterByOrganization(queryParams.orgParams)
      .filterByUser(queryParams.userParams)
      .filterByTimestamp(queryParams.timestampParams)

    val paginatedProjects = database.db.run {
      val action = projects.page(pageRequest).result
      logger.debug(s"Query for projects -- SQL ${action.statements.headOption}")
      action
    }

    val totalProjectsQuery = database.db.run { projects.length.result }

    for {
      totalProjects <- totalProjectsQuery
      projects <- paginatedProjects
    } yield {
      val hasNext = (pageRequest.offset + 1) * pageRequest.limit < totalProjects
      val hasPrevious = pageRequest.offset > 0
      PaginatedResponse[Project](totalProjects, hasPrevious, hasNext,
        pageRequest.offset, pageRequest.limit, projects)
    }
  }

  /** Delete a given project from the database
    *
    * @param projectId UUID primary key of project to delete
    * @param user      Results will be limited to user's organization
    */
  def deleteProject(projectId: UUID, user: User)(implicit database: DB): Future[Int] = {
    for {
      projectAois <- database.db.run {
        AoisToProjects
          .filter(_.projectId === projectId)
          .map(_.aoiId)
          .result
      }

      deleteProjectAois <- Future.sequence(projectAois.map { aoi =>
        database.db.run {
          AoisToProjects
            .filter(_.aoiId === aoi)
            .delete
        }
      })

      deleteAois <- Future.sequence(projectAois.map { aoi =>
        database.db.run {
          AOIs.filterToSharedOrganizationIfNotInRoot(user)
            .filter(_.id === aoi )
            .delete
        }
      })

      deleteProject <- database.db.run {
        Projects
          .filterToSharedOrganizationIfNotInRoot(user)
          .filter(_.id === projectId)
          .delete
      }
    } yield {
      deleteProject
    }
  }

  /** Update a given project
    *
    * Currently allows updating the following attributes of a project:
    *  - name
    *  - description
    *  - visibility
    *  - tags
    *
    * Separate functions exist to remove/add scenes to a project
    *
    * @param project Project project with updated values
    * @param projectId UUID primary key of project to update
    * @param user User user updating project values
    */
  def updateProject(project: Project, projectId: UUID, user: User)
                  (implicit database: DB): Future[Int] = {

    val updateTime = new Timestamp((new Date).getTime)

    val updateProjectQuery = for {
      updateProject <- Projects
                         .filterToSharedOrganizationIfNotInRoot(user)
                         .filter(_.id === projectId)
    } yield (
      updateProject.modifiedAt, updateProject.modifiedBy, updateProject.name, updateProject.description,
      updateProject.visibility, updateProject.tileVisibility, updateProject.tags, updateProject.aoiCadenceMillis,
      updateProject.aoisLastChecked, updateProject.isSingleBand, updateProject.singleBandOptions
    )
    database.db.run {
      updateProjectQuery.update(
        (updateTime, user.id, project.name, project.description, project.visibility, project.tileVisibility, project.tags,
         project.aoiCadenceMillis, project.aoisLastChecked, project.isSingleBand, project.singleBandOptions)
      )
    } map {
      case 1 => 1
      case c => throw new IllegalStateException(s"Error updating project: update result expected to be 1, was $c")
    }
  }

  /** Adds a list of scenes to a project
    *
    * @param sceneIds Seq[UUID] list of primary keys of scenes to add to project
    * @param projectId UUID primary key of back to add scenes to
    */
  def addScenesToProject(sceneIds: Seq[UUID], projectId: UUID, user: User)
                        (implicit database: DB): Future[Iterable[Scene.WithRelated]] = {
    // Users should not be able to add scenes to a project they did not create (for now)
    val authProjectCount =
      Projects.filter(_.id === projectId).filter(_.createdBy === user.id).length.result

    // Users should only be able to add scenes they are authorized to view to a project
    val authSceneCount =
      Scenes.filter(_.id inSet sceneIds).filterUserVisibility(user).length.result

    database.db.run {
      authProjectCount zip authSceneCount
    } flatMap { r: (Int, Int) =>
      (r._1, r._2) match {
        case (projectCount, sceneCount)
            if projectCount == 1 &&
            sceneCount == sceneIds.length ||
            user.isInRootOrganization => {
          // Only allow this action to be performed if authorization is perfect
          // The user must be authorized to modify the project and must have access to ALL scenes
          // or the user must be part of the root organization
          val sceneProjectJoinQuery = for {
            s <- ScenesToProjects if s.sceneId.inSet(sceneIds) && s.projectId === projectId
          } yield s.sceneId

          database.db.run {
            sceneProjectJoinQuery.result
          } flatMap { alreadyAdded =>
            val newSceneIds = sceneIds.filterNot(alreadyAdded.toSet)
            val newScenesFootprints = Scenes.getScenesFootprints(sceneIds)
            val projectAction = Projects.filter(_.id === projectId).result.headOption

            database.db.run(
              for {
                footprints <- newScenesFootprints
                projectOption <- projectAction
              } yield (footprints, projectOption)
            ) map { p: (Seq[Option[Projected[Geometry]]], Option[Project]) =>
              (p._1, p._2) match {
                case (footprints, Some(project)) => {
                  val newScenesExtent = getFootprintsExtent(footprints)

                  val newProjectExtent:Option[Extent] = (project.extent, newScenesExtent) match {
                    case (Some(projectExtent), Some(scenesExtent)) => Some(projectExtent.envelope.combine(scenesExtent))
                    case (Some(projectExtent), _) => Some(projectExtent.geom.envelope)
                    case (_, Some(scenesExtent)) => Some(scenesExtent)
                    case _ => None
                  }

                  val newProjectExtentGeometry = newProjectExtent match {
                    case Some(ext) => Some(new Projected(ext.toPolygon(), 3857))
                    case _ => None
                  }

                  val updateProjectExtentQuery = for {
                    updateProject <- Projects.filter(_.id === projectId)
                  } yield (
                    updateProject.extent
                  )

                  database.db.run {
                    updateProjectExtentQuery.update(newProjectExtentGeometry)
                  }
                }
                case _ => throw IllegalRequestException(StatusCodes.ClientError(404)("Not Found", "Error updating project: no project matching id found"))
              }
            }

            val sceneCompositesQuery = for {
              s <- Scenes if s.id.inSet(newSceneIds)
              d <- Datasources if d.id === s.datasource
            } yield (s.id, d.composites)

            database.db.run {
              sceneCompositesQuery.result
            } flatMap { sceneComposites =>
              val sceneToProjects = sceneComposites.map { case (sceneId, composites) =>
                val redBandPath = root.natural.selectDynamic("value").redBand.int
                val greenBandPath = root.natural.selectDynamic("value").greenBand.int
                val blueBandPath = root.natural.selectDynamic("value").blueBand.int

                val redBand = redBandPath.getOption(composites).getOrElse(0)
                val greenBand = greenBandPath.getOption(composites).getOrElse(1)
                val blueBand = blueBandPath.getOption(composites).getOrElse(2)

                SceneToProject(
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
                      AutoWhiteBalance(false)                       // Auto White Balance
                    )
                  )
                )
              }

              logger.info(s"Number of sceneToProject inserts: ${sceneToProjects.length}")
              database.db.run {
                ScenesToProjects.forceInsertAll(sceneToProjects)
              } flatMap {
                _ => {
                  val scenesNotIngestedQuery = for {
                    s <- Scenes if s.id.inSet(sceneIds) && s.ingestStatus.inSet(
                      Set(IngestStatus.NotIngested, IngestStatus.Failed))
                  } yield (s.ingestStatus)

                  database.db.run {
                    scenesNotIngestedQuery.update((IngestStatus.ToBeIngested))
                  }

                  logger.info(s"Scene IDs right at the end: $sceneIds")
                  listSelectProjectScenes(projectId, sceneIds)
                }
              }
            }
          }

        }
        case (0, _) => throw IllegalRequestException(StatusCodes.ClientError(403)("Forbidden", "Error updating project: not authorized to modify this project"))
        case (_, _) => throw IllegalRequestException(StatusCodes.ClientError(403)("Forbidden", "Error updating project: not authorized to use one or more of these scenes"))
      }
    }
  }

  def addScenesToProjectFromQuery(combinedParams: CombinedSceneQueryParams, projectId: UUID, user: User)
                                 (implicit database: DB): Future[Iterable[Scene.WithRelated]] = {
    val sceneAction = Scenes.filterScenes(combinedParams)
      .filterUserVisibility(user)
      .map(_.id)
      .result
    database.db.run(sceneAction) flatMap { ids =>
      Projects.addScenesToProject(ids, projectId, user)
    }
  }

  /** Removes scenes from project
    *
    * @param sceneIds Seq[UUID] primary key of scenes to remove from project
    * @param projectId UUID primary key of project these scenes will be removed from
    */
  def deleteScenesFromProject(sceneIds: Seq[UUID], projectId: UUID)
                            (implicit database: DB): Future[Int] = {
    val remainingScenesFootprintsQuery = Scenes
      .filter { scene =>
        scene.id in ScenesToProjects.filter(_.projectId === projectId).filterNot(_.sceneId inSet sceneIds).map(_.sceneId)
      }
      .map(_.dataFootprint)

    database.db.run {
      remainingScenesFootprintsQuery.result
    } flatMap { remainingScenesFootprints =>
      val extent = getFootprintsExtent(remainingScenesFootprints)

      val extentPolygon = extent match {
        case Some(ext) => Some(new Projected(ext.toPolygon(), 3857))
        case _ => None
      }

      val updateProjectExtentQuery = for {
        updateProject <- Projects.filter(_.id === projectId)
      } yield (
        updateProject.extent
      )

      val sceneProjectJoinQuery = ScenesToProjects.filter(_.sceneId.inSet(sceneIds)).filter(_.projectId === projectId)

      val actions = for {
        project <- updateProjectExtentQuery.update(extentPolygon)
        s <- sceneProjectJoinQuery.delete
      } yield s

      database.db.run {
        actions
      }
    }
  }

  /** Removes all scenes from project, then adds all specified scenes to it
    *
    * @param sceneIds Seq[UUID] primary key of scenes to add to project
    * @param projectId UUID primary key of project to remove all scenes from and add specified scenes to
    * @return Scenes that were added
    */
  def replaceScenesInProject(sceneIds: Seq[UUID], projectId: UUID)
                           (implicit database: DB): Future[Iterable[Scene.WithRelated]] = {

    val scenesToProjects = sceneIds.map { sceneId =>
      SceneToProject(sceneId, projectId, true)
    }

    val actions = DBIO.seq(
      ScenesToProjects.filter(_.projectId === projectId).delete,
      ScenesToProjects.forceInsertAll(scenesToProjects)
    )

    database.db.run {
      actions.transactionally
    }

    listSelectProjectScenes(projectId, sceneIds)
  }

  def getFootprintsExtent(footprints: Seq[Option[Projected[Geometry]]]):Option[Extent] = {
    val footprintEnvelopes = footprints.flatten map { _.geom.envelope }
    footprintEnvelopes.length match {
      case 0 => None
      case _ => Some(footprintEnvelopes reduce { _ combine _ })
    }
  }
}
