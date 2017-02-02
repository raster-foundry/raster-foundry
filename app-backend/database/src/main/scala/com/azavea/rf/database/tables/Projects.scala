package com.azavea.rf.database.tables

import com.azavea.rf.database.fields._
import com.azavea.rf.database.sort._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._

import com.lonelyplanet.akka.http.extensions._
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import java.util.UUID
import java.util.Date
import java.sql.Timestamp
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


/** Table description of table projects. Objects of this class serve as prototypes for rows in queries. */
class Projects(_tableTag: Tag) extends Table[Project](_tableTag, "projects")
                                      with ProjectFields
                                      with OrganizationFkFields
                                      with UserFkVisibileFields
                                      with TimestampFields
{
  def * = (id, createdAt, modifiedAt, organizationId, createdBy, modifiedBy, name, slugLabel, description, visibility, tags, manualOrder) <> (Project.tupled, Project.unapply)

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val name: Rep[String] = column[String]("name")
  val slugLabel: Rep[String] = column[String]("slug_label", O.Length(255,varying=true))
  val description: Rep[String] = column[String]("description")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val tags: Rep[List[String]] = column[List[String]]("tags", O.Length(2147483647,varying=false), O.Default(List.empty))
  val manualOrder: Rep[Boolean] = column[Boolean]("manual_order", O.Default(true))

  lazy val organizationsFk = foreignKey("projects_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("projects_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("projects_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
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

  /** Get project given a projectId
    *
    * @param projectId UUID primary key of project to retrieve
    */
  def getProject(projectId: UUID)
               (implicit database: DB): Future[Option[Project]] = {

    database.db.run {
      Projects.filter(_.id === projectId).result.headOption
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
    */
  def deleteProject(projectId: UUID)(implicit database: DB): Future[Int] = {

    database.db.run {
      Projects.filter(_.id === projectId).delete
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
      updateProject <- Projects.filter(_.id === projectId)
    } yield (
      updateProject.modifiedAt, updateProject.modifiedBy, updateProject.name, updateProject.description,
      updateProject.visibility, updateProject.tags
    )
    database.db.run {
      updateProjectQuery.update((
        updateTime, user.id, project.name, project.description, project.visibility, project.tags
      ))
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
  def addScenesToProject(sceneIds: Seq[UUID], projectId: UUID)
                       (implicit database: DB): Future[Iterable[Scene.WithRelated]] = {

    val sceneProjectJoinQuery = for {
      s <- ScenesToProjects if s.sceneId.inSet(sceneIds) && s.projectId === projectId
    } yield s.sceneId

    database.db.run {
      sceneProjectJoinQuery.result
    } map { alreadyAdded =>
      val newScenes = sceneIds.filterNot(alreadyAdded.toSet)
      val newScenesToProjects = newScenes.map { sceneId =>
        SceneToProject(sceneId, projectId)
      }

      database.db.run {
        ScenesToProjects.forceInsertAll(newScenesToProjects)
      }
    }

    val scenesNotIngestedQuery = for {
      s <- Scenes if s.id.inSet(sceneIds) && s.ingestStatus.inSet(
        Set(IngestStatus.NotIngested, IngestStatus.Failed))
    } yield (s.ingestStatus)

    database.db.run {
      scenesNotIngestedQuery.update((IngestStatus.ToBeIngested))
    }

    listSelectProjectScenes(projectId, sceneIds)
  }

  /** Removes scenes from project
    *
    * @param sceneIds Seq[UUID] primary key of scenes to remove from project
    * @param projectId UUID primary key of project these scenes will be removed from
    */
  def deleteScenesFromProject(sceneIds: Seq[UUID], projectId: UUID)
                            (implicit database: DB): Future[Int] = {

    val sceneProjectJoinQuery = for {
      s <- ScenesToProjects if s.sceneId.inSet(sceneIds) && s.projectId === projectId
    } yield s

    database.db.run {
      sceneProjectJoinQuery.delete
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
      SceneToProject(sceneId, projectId)
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
}
