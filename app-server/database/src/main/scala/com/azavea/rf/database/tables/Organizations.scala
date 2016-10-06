package com.azavea.rf.database.tables

import java.util.UUID
import java.sql.Timestamp
import com.azavea.rf.datamodel._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import org.postgresql.util.PSQLException
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.LazyLogging

/** Table description of table organizations. Objects of this class serve as prototypes for rows in queries. */
class Organizations(_tableTag: Tag) extends Table[Organization](_tableTag, "organizations")
                                            with HasTimestamp
{
  def * = (id, createdAt, modifiedAt, name) <> (Organization.tupled, Organization.unapply)
  /** Maps whole row to an option. Useful for outer joins. */
  def ? = (Rep.Some(id), Rep.Some(createdAt), Rep.Some(modifiedAt), Rep.Some(name)).shaped.<>({r=>import r._; _1.map(_=> Organization.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val name: Rep[String] = column[String]("name", O.Length(255,varying=true))
}

object Organizations extends TableQuery(tag => new Organizations(tag)) with LazyLogging {
  type TableQuery = Query[Organizations, Organizations#TableElementType, Seq]

  def applyOrgSort(query: Organizations.TableQuery, sortMap: Map[String, Order])
                  (implicit database: DB, ec: ExecutionContext): Organizations.TableQuery = {
    import database.driver.api._

    sortMap.headOption match {
      case Some(("id", order)) =>
        order match {
          case Order.Asc => {
            val sortQuery = query.sortBy(_.id.asc)
            logger.debug(s"Org sort query to run: $sortQuery")
            applyOrgSort(sortQuery, sortMap.tail)
          }
          case Order.Desc => {
            val sortQuery = query.sortBy(_.id.desc)
            logger.debug(s"Org sort query to run: $sortQuery")
            applyOrgSort(sortQuery, sortMap.tail)
          }
        }
      case Some(("name", order)) =>
        order match {
          case Order.Asc => {
            val sortQuery = query.sortBy(_.name.asc)
            logger.debug(s"Org sort query to run: $sortQuery")
            applyOrgSort(sortQuery, sortMap.tail)
          }
          case Order.Desc => {
            val sortQuery = query.sortBy(_.name.desc)
            logger.debug(s"Org sort query to run: $sortQuery")
            applyOrgSort(sortQuery, sortMap.tail)
          }
        }
      case Some(("modified", order)) =>
        order match {
          case Order.Asc => {
            val sortQuery = query.sortBy(_.modifiedAt.asc)
            logger.debug(s"Org sort query to run: $sortQuery")
            applyOrgSort(sortQuery, sortMap.tail)
          }
          case Order.Desc => {
            val sortQuery = query.sortBy(_.modifiedAt.desc)
            logger.debug(s"Org sort query to run: $sortQuery")
            applyOrgSort(sortQuery, sortMap.tail)
          }
        }
      case Some(("created", order)) =>
        order match {
          case Order.Asc => {
            val sortQuery = query.sortBy(_.createdAt.asc)
            logger.debug(s"Org sort query to run: $sortQuery")
            applyOrgSort(sortQuery, sortMap.tail)
          }
          case Order.Desc => {
            val sortQuery = query.sortBy(_.createdAt.desc)
            logger.debug(s"Org sort query to run: $sortQuery")
            applyOrgSort(sortQuery, sortMap.tail)
          }
        }
      case Some((_, order)) => applyOrgSort(query, sortMap.tail)
      case _ => query
    }
  }

  def applyUserRoleSort(
    query: Query[UsersToOrganizations, UsersToOrganizations#TableElementType, Seq],
    sortMap: Map[String, Order]
  )(implicit database: DB, ec: ExecutionContext):
      Query[UsersToOrganizations, UsersToOrganizations#TableElementType, Seq] = {
    import database.driver.api._

    sortMap.headOption match {
      case Some(("id", order)) =>
        order match {
          case Order.Asc => {
            val sortQuery = query.sortBy(_.userId.asc)
            logger.debug(s"User role sort query: ${sortQuery.result.statements.headOption}")
            applyUserRoleSort(sortQuery, sortMap.tail)
          }
          case Order.Desc => {
            val sortQuery = query.sortBy(_.userId.desc)
            logger.debug(s"User role sort query: ${sortQuery.result.statements.headOption}")
            applyUserRoleSort(query.sortBy(_.userId.desc), sortMap.tail)
          }
        }
      case Some(("role", order)) =>
        order match {
          case Order.Asc => {
            val sortQuery = query.sortBy(_.role.asc)
            logger.debug(s"User role sort query: ${sortQuery.result.statements.headOption}")
            applyUserRoleSort(sortQuery, sortMap.tail)
          }
          case Order.Desc => {
            val sortQuery = query.sortBy(_.role.asc)
            logger.debug(s"User role sort query: ${sortQuery.result.statements.headOption}")
            applyUserRoleSort(query.sortBy(_.role.desc), sortMap.tail)
          }
        }
      case Some(("modified", order)) =>
        order match {
          case Order.Asc => {
            val sortQuery = query.sortBy(_.modifiedAt.asc)
            logger.debug(s"User role sort query: ${sortQuery.result.statements.headOption}")
            applyUserRoleSort(sortQuery, sortMap.tail)
          }
          case Order.Desc => {
            val sortQuery = query.sortBy(_.modifiedAt.desc)
            logger.debug(s"User role sort query: ${sortQuery.result.statements.headOption}")
            applyUserRoleSort(sortQuery, sortMap.tail)
          }
        }
      case Some(("created", order)) =>
        order match {
          case Order.Asc => {
            val sortQuery =  query.sortBy(_.createdAt.asc)
            logger.debug(s"User role sort query: ${sortQuery.result.statements.headOption}")
            applyUserRoleSort(sortQuery, sortMap.tail)
          }
          case Order.Desc => {
            val sortQuery = query.sortBy(_.createdAt.desc)
            logger.debug(s"User role sort query: ${sortQuery.result.statements.headOption}")
            applyUserRoleSort(sortQuery, sortMap.tail)
          }
        }
      case Some((_, order)) => {
        logger.debug(s"User role sort query: ${query.result.statements.headOption}")
        applyUserRoleSort(query, sortMap.tail)
      }
      case _ => query
    }
  }

  def getOrganizationList(page: PageRequest)(implicit database: DB, ec: ExecutionContext):
      Future[PaginatedResponse[Organization]] = {
    import database.driver.api._

    val organizationsQueryResult = database.db.run {
      applyOrgSort(Organizations, page.sort)
        .drop(page.offset * page.limit)
        .take(page.limit)
        .result
    }
    val totalOrganizationsQuery = database.db.run {
      Organizations.length.result
    }

    for {
      totalOrganizations <- totalOrganizationsQuery
      organizations <- organizationsQueryResult
    } yield {
      val hasNext = (page.offset + 1) * page.limit < totalOrganizations // 0 indexed page offset
      val hasPrevious = page.offset > 0
      PaginatedResponse(totalOrganizations, hasPrevious, hasNext,
        page.offset, page.limit, organizations)
    }
  }

  def getOrganization(id: java.util.UUID)(implicit database: DB):
      Future[Option[Organization]] = {
    import database.driver.api._

    val action = Organizations.filter(_.id === id).result
    logger.debug(s"Query for org $id: ${action.statements.headOption}")
    database.db.run {
      action.headOption
    }
  }

  def createOrganization(
    org: OrganizationCreate
  )(implicit database: DB, ec: ExecutionContext): Future[Try[Organization]] = {
    import database.driver.api._

    val rowInsert = org.toOrganization()

    val action = Organizations.forceInsert(rowInsert)
    logger.debug(s"Inserting org with: ${action.statements.headOption}")
    database.db.run {
      action.asTry
    } map {
      case Success(res) => {
        res match {
          case 1 => Success(rowInsert)
          case _ => Failure(
            new Exception(
              s"Unexpected result from database when inserting organization: $res"
            )
          )
        }
      }
      case Failure(e) => {
        e match {
          case e: PSQLException => {
            Failure(new IllegalStateException("Organization already exists"))
          }
          case _ => Failure(e)
        }
      }
    }
  }

  def updateOrganization(
    org: Organization, id: java.util.UUID
  )(implicit database: DB, ec: ExecutionContext): Future[Try[Int]] = {
    import database.driver.api._

    val now = new Timestamp((new java.util.Date()).getTime())
    val updateQuery = for {
      updateorg <- Organizations.filter(_.id === id)
    } yield (
      updateorg.name, updateorg.modifiedAt
    )
    val action = updateQuery.update((org.name, now))
    logger.debug(s"Updating org with: ${action.statements.headOption}")
    database.db.run {
      action.asTry
    } map {
      case Success(res) => {
        res match {
          case 1 => Success(1)
          case _ => Failure(new Exception("Error while updating organization: Unexpected result"))
        }
      }
      case Failure(e) => Failure(e)
    }
  }

  def getOrganizationUsers(
    page: PageRequest, id: java.util.UUID
  )(implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[UserWithRole]] = {
    import database.driver.api._

    val getOrgUsersResult = database.db.run {
      applyUserRoleSort(UsersToOrganizations.filter(_.organizationId === id), page.sort)
        .drop(page.offset * page.limit)
        .take(page.limit)
        .result
    } map {
      rels => rels.map(rel => UserWithRole(rel.userId, rel.role, rel.createdAt, rel.modifiedAt))
    }

    val totalOrgUsersResult = database.db.run {
      UsersToOrganizations.filter(_.organizationId === id).length.result
    }

    for {
      totalOrgUsers <- totalOrgUsersResult
      orgUsers <- getOrgUsersResult
    } yield {
      val hasNext = (page.offset + 1) * page.limit < totalOrgUsers // 0 indexed page offset
      val hasPrevious = page.offset > 0
      PaginatedResponse(totalOrgUsers, hasPrevious, hasNext, page.offset, page.limit, orgUsers)
    }
  }

  def getOrganizationUser(
    orgId: java.util.UUID, userId: String
  )(implicit database: DB, ex: ExecutionContext): Future[Option[UserWithRole]] = {
    import database.driver.api._

    val getOrgUserQuery = for {
      relationship <- UsersToOrganizations.filter(_.userId === userId)
        .filter(_.organizationId === orgId)
      user <- Users.filter(_.id === relationship.userId)
    } yield (user.id, relationship.role, relationship.createdAt, relationship.modifiedAt)
    val action = getOrgUserQuery.result
    logger.debug(s"Getting org user with: ${action.statements.headOption}")
    database.db.run {
      action.headOption
    } map {
      case Some(tuple) => Option(UserWithRole.tupled(tuple))
      case _ => None
    }
  }

  def addUserToOrganization(
    userWithRoleCreate: UserWithRoleCreate, orgId: java.util.UUID
  )(implicit database: DB, ex: ExecutionContext): Future[Try[UserWithRole]] = {
    import database.driver.api._

    val userWithRole = userWithRoleCreate.toUserWithRole()

    val insertRow = UserToOrganization(
      userWithRole.id, orgId, userWithRole.role, userWithRole.createdAt, userWithRole.modifiedAt
    )

    val action = UsersToOrganizations.forceInsert(insertRow)
    logger.debug(s"Inserting User to Org with: ${action.statements.headOption}")
    database.db.run {
      action.asTry
    } map {
      case Success(user) => Success(userWithRole)
      case Failure(_) => throw new IllegalStateException("User is already in the organization")
    }
  }

  def getUserOrgRole(
    userId: String, orgId: java.util.UUID
  )(implicit database: DB, ex: ExecutionContext): Future[Option[UserWithRole]] = {
    import database.driver.api._

    val action = UsersToOrganizations.filter(
      rel => rel.userId === userId && rel.organizationId === orgId
    ).result
    logger.debug(s"Getting user org role with: ${action.statements.headOption}")
    database.db.run {
      action.headOption
    } map {
      case Some(rel) => Some(UserWithRole(rel.userId, rel.role, rel.createdAt, rel.modifiedAt))
      case _ => None
    }
  }

  def deleteUserOrgRole(
    userId: String, orgId: java.util.UUID
  )(implicit database: DB): Future[Int] = {
    import database.driver.api._

    val action = UsersToOrganizations.filter(
      rel => rel.userId === userId && rel.organizationId === orgId
    ).delete
    logger.debug(s"Deleting User to Org with: ${action.statements.headOption}")
    database.db.run {
      action
    }
  }

  def updateUserOrgRole(
    userWithRole: UserWithRole, orgId: java.util.UUID, userId: String
  )(implicit database: DB): Future[Try[Int]] = {
    import database.driver.api._

    userWithRole.role match {
      case UserRoles(_) => {
        val now = new Timestamp((new java.util.Date()).getTime())

        val rowUpdate = for {
          relationship <- UsersToOrganizations.filter(
            rel => rel.userId === userId && rel.organizationId === orgId
          )
        } yield (
          relationship.modifiedAt, relationship.role
        )

        val action = rowUpdate.update(
          (now, userWithRole.role)
        )
        logger.debug(s"Updating user with role with: ${action.statements.headOption}")

        database.db.run {
          action.asTry
        }
      }
      case invalidRole: String => throw new IllegalArgumentException(
        s"$invalidRole is not a valid User role"
      )
    }
  }
}
