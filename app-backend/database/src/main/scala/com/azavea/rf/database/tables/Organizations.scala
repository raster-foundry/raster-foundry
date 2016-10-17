package com.azavea.rf.database.tables

import java.util.UUID
import java.sql.Timestamp
import com.azavea.rf.database.fields.{NameField, TimestampFields}
import com.azavea.rf.database.sort._
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
                                            with NameField
                                            with TimestampFields
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

  implicit val sorter =
    new QuerySorter[Organizations](
      new NameFieldSort(identity[Organizations]),
      new TimestampSort(identity[Organizations]))

  def getOrganizationList(page: PageRequest)(implicit database: DB): Future[PaginatedResponse[Organization]] = {

    val organizationsQueryResult = database.db.run {
      Organizations
        .drop(page.offset * page.limit)
        .take(page.limit)
        .sort(page.sort)
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
    org: Organization.Create
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
  )(implicit database: DB, ec: ExecutionContext): Future[PaginatedResponse[User.WithRole]] = {
    import database.driver.api._

    val getOrgUsersResult = database.db.run {
      UsersToOrganizations.filter(_.organizationId === id)
        .drop(page.offset * page.limit)
        .take(page.limit)
        .sort(page.sort)
        .result
    } map {
      rels => rels.map(rel => User.WithRole(rel.userId, rel.role, rel.createdAt, rel.modifiedAt))
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
  )(implicit database: DB, ex: ExecutionContext): Future[Option[User.WithRole]] = {
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
      case Some(tuple) => Option(User.WithRole.tupled(tuple))
      case _ => None
    }
  }

  def addUserToOrganization(
    userWithRoleCreate: User.WithRoleCreate, orgId: java.util.UUID
  )(implicit database: DB, ex: ExecutionContext): Future[Try[User.WithRole]] = {
    import database.driver.api._

    val userWithRole = userWithRoleCreate.toUserWithRole()

    val insertRow =
      User.ToOrganization(userWithRole.id, orgId, userWithRole.role, userWithRole.createdAt, userWithRole.modifiedAt)

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
  )(implicit database: DB, ex: ExecutionContext): Future[Option[User.WithRole]] = {
    import database.driver.api._

    val action = UsersToOrganizations.filter(
      rel => rel.userId === userId && rel.organizationId === orgId
    ).result
    logger.debug(s"Getting user org role with: ${action.statements.headOption}")
    database.db.run {
      action.headOption
    } map {
      case Some(rel) => Some(User.WithRole(rel.userId, rel.role, rel.createdAt, rel.modifiedAt))
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

  def updateUserOrgRole(userWithRole: User.WithRole, orgId: java.util.UUID, userId: String)(implicit database: DB): Future[Try[Int]] = {
    import database.driver.api._

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
}
