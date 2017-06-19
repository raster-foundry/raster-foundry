package com.azavea.rf.database.tables

import java.util.UUID
import java.sql.Timestamp

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{TimestampFields, UserFields}
import com.azavea.rf.database.sort._
import com.azavea.rf.datamodel._
import com.typesafe.scalalogging.LazyLogging
import com.lonelyplanet.akka.http.extensions.{Order, PageRequest}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import slick.dbio.DBIO

class Users(_tableTag: Tag) extends Table[User](_tableTag, "users")
                                    with UserFields
                                    with TimestampFields
{
  def * = (id, organizationId, role, createdAt, modifiedAt, dropboxCredential) <>
    (User.tupled, User.unapply)

  val id: Rep[String] = column[String]("id", O.PrimaryKey, O.Length(255,varying=true))
  val organizationId: Rep[UUID] = column[UUID]("organization_id")
  val role: Rep[UserRole] = column[UserRole]("role", O.Length(255,varying=true))
  val createdAt: Rep[Timestamp] = column[Timestamp]("created_at")
  val modifiedAt: Rep[Timestamp] = column[Timestamp]("modified_at")
  val dropboxCredential: Rep[Option[String]] = column[Option[String]]("dropbox_credential")
}

object Users extends TableQuery(tag => new Users(tag)) with LazyLogging {
  type TableQuery = Query[Users, Users#TableElementType, Seq]
  type WithOrgQuery = (Users, Organizations)

  implicit val sorter = new QuerySorter[Users](
    new UserFieldSort(identity[Users]),
    new TimestampSort(identity[Users]))

  /**
    * Returns a paginated result with Users
    *
    * @param page page request that has limit, offset, and sort parameters
    */
  def listUsers(page: PageRequest)(implicit database: DB):
    Future[PaginatedResponse[User]] = {

    val usersQueryResult = database.db.run {
      Users
        .drop(page.offset * page.limit)
        .take(page.limit)
        .sort(page.sort)
        .result
    }
    val totalUsersQuery = database.db.run {
      Users.length.result
    }

    for {
      totalUsers <- totalUsersQuery
      users <- usersQueryResult
    } yield {
      val hasNext = (page.offset + 1) * page.limit < totalUsers // 0 indexed page offset
      val hasPrevious = page.offset > 0
      PaginatedResponse(totalUsers, hasPrevious, hasNext,
                        page.offset, page.limit, users)
    }
  }

  /**
    * Recursively applies a list of sort parameters from a page request
    */
  def applySort(query: Users.TableQuery, sortMap: Map[String, Order])
               (implicit database: DB): Users.TableQuery = {

    logger.debug(s"Returning users -- SQL: ${query.result.statements.headOption}")

    sortMap.headOption match {
      case Some(("id", order)) =>
        order match {
          case Order.Asc => applySort(query.sortBy(_.id.asc), sortMap.tail)
          case Order.Desc => applySort(query.sortBy(_.id.desc), sortMap.tail)
        }
      case Some((_, order)) => applySort(query, sortMap.tail)
      case _ => query
    }
  }

  def createUser(user: User.Create)(implicit database: DB):  Future[User] = {
    val userRow = user.toUser

    val insertAction = Users.forceInsert(userRow)

    logger.debug(s"Inserting user -- User SQL: ${insertAction.statements.headOption}")

    database.db.run {
      insertAction.map(_ => userRow)
    }
  }

  def cloneProjectsFromDefault(user: User)(implicit database: DB): Future[Unit] = {
     // copy the projects, with corrected user information / owner
     val defaultUserId = "default_projects"
     val now = new Timestamp((new java.util.Date()).getTime)
     val insertProjectsAction = for {
       projects <- Projects.filter(_.owner === defaultUserId).result
       insertedProjects <- (Projects returning Projects.map(_.id)).forceInsertAll(projects.map(project =>
         project.copy(id = UUID.randomUUID(), createdAt = now, modifiedAt = now, owner=user.id)
       ))
       result <- DBIO.sequence(
         insertedProjects.zip(projects.map(_.id)) map { case (newId, oldId) =>
           for {
             scenesToProjects <- ScenesToProjects.filter(_.projectId === oldId).result
             insertedScenesToProjects <- (ScenesToProjects returning ScenesToProjects).forceInsertAll(scenesToProjects.map(sceneToProject =>
             sceneToProject.copy(projectId=newId)))
           } yield ()
         }
       )
     } yield ()

     database.db.run {
       insertProjectsAction
     }
  }

  def createUserWithAuthId(sub: String)(implicit database: DB): Future[User] = {

    database.db.run {
      Organizations.filter(_.name === "Public").result.headOption
    } flatMap {
      case Some(org) =>
        createUser(User.Create(sub, org.id)) map { user =>
          cloneProjectsFromDefault(user)
          user
        }

      case _ =>
        throw new Exception("No public org found in database")
    }
  }

  def getUserById(id: String)(implicit database: DB): Future[Option[User]] = {
    val getUserAction = Users.filter(_.id === id).result
    logger.debug(s"Attempting to retrieve user $id -- SQL: ${getUserAction.statements.headOption})")

    database.db.run {
      getUserAction.headOption
    }
  }

  def storeDropboxAccessToken(userId: String, token: String)(implicit database: DB): Future[Int] = {
    val updateAction = Users.filter(_.id === userId).map(_.dropboxCredential).update(Some(token))
    logger.debug(s"Attempting to store access token for user $userId")
    database.db.run(updateAction)
  }

  def getDropboxAccessToken(userId: String)(implicit database: DB): Future[Option[Option[String]]] = {
    val filtAction = Users.filter(_.id === userId).map(_.dropboxCredential).result.headOption
    logger.debug(s"Attempting to retrieve access token for user $userId")
    database.db.run(filtAction)
  }

  def updateUser(user: User, id: String)(implicit database: DB): Future[Int] = {
    val now = new Timestamp((new java.util.Date).getTime)
    val updateQuery = for {
      u <- Users.filter(_.id === id)
    } yield (
      u.organizationId, u.role, u.modifiedAt
    )

    database.db.run {
      updateQuery.update((user.organizationId, user.role, now))
    } map {
      case 1 => 1
      case _ => throw new IllegalStateException("Error while updating user: Unexpected result")
    }
  }
}
