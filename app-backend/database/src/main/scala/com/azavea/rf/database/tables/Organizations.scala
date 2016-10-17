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
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Success, Failure}

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

  def getOrganization(id: java.util.UUID)(implicit database: DB): Future[Option[Organization]] = {
    val action = Organizations.filter(_.id === id).result
    logger.debug(s"Query for org $id: ${action.statements.headOption}")
    database.db.run {
      action.headOption
    }
  }

  def createOrganization(org: Organization.Create)(implicit database: DB): Future[Organization] = {
    val rowInsert = org.toOrganization()

    val action = Organizations.forceInsert(rowInsert)
    logger.debug(s"Inserting org with: ${action.statements.headOption}")
    database.db.run {
      action.asTry
    } map {
      case Success(res) =>
        res match {
          case 1 => rowInsert
          case _ =>
            throw new Exception(
              s"Unexpected result from database when inserting organization: $res"
            )
        }
      case Failure(e) =>
        e match {
          case e: PSQLException =>
            throw new IllegalStateException("Organization already exists")
          case _ => throw e
        }
    }
  }

  def updateOrganization(org: Organization, id: UUID)(implicit database: DB): Future[Int] = {
    val now = new Timestamp((new java.util.Date).getTime)
    val updateQuery = for {
      updateorg <- Organizations.filter(_.id === id)
    } yield (
      updateorg.name, updateorg.modifiedAt
    )
    val action = updateQuery.update((org.name, now))
    logger.debug(s"Updating org with: ${action.statements.headOption}")
    database.db.run {
      action.map {
        case 1 => 1
        case _ => throw new IllegalStateException("Error while updating organization: Unexpected result")
      }
    }
  }

  def getOrganizationUsers(
    page: PageRequest, id: java.util.UUID
  )(implicit database: DB): Future[PaginatedResponse[User.WithRole]] = {
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

  def getOrganizationUser(orgId: UUID, userId: String)(implicit database: DB): Future[Option[User.WithRole]] = {
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

  def addUserToOrganization(userWithRoleCreate: User.WithRoleCreate, orgId: UUID)
    (implicit database: DB): Future[User.WithRole] = {
    val userWithRole = userWithRoleCreate.toUserWithRole()

    val insertRow =
      User.ToOrganization(userWithRole.id, orgId, userWithRole.role, userWithRole.createdAt, userWithRole.modifiedAt)

    val action = UsersToOrganizations.forceInsert(insertRow)
    logger.debug(s"Inserting User to Org with: ${action.statements.headOption}")
    database.db.run {
      action.asTry
    } map {
      case Success(user) => userWithRole
      case Failure(_) => throw new IllegalStateException("User is already in the organization")
    }
  }

  def getUserOrgRole(userId: String, orgId: UUID)
    (implicit database: DB): Future[Option[User.WithRole]] = {
    val action = UsersToOrganizations.filter(
      rel => rel.userId === userId && rel.organizationId === orgId
    ).result
    logger.debug(s"Getting user org role with: ${action.statements.headOption}")
    database.db.run {
      action.headOption.map {
        case Some(rel) => Some(User.WithRole(rel.userId, rel.role, rel.createdAt, rel.modifiedAt))
        case _ => None
      }
    }
  }

  def deleteUserOrgRole(userId: String, orgId: UUID)(implicit database: DB): Future[Int] = {
    val action = UsersToOrganizations.filter(
      rel => rel.userId === userId && rel.organizationId === orgId
    ).delete
    logger.debug(s"Deleting User to Org with: ${action.statements.headOption}")
    database.db.run {
      action
    }
  }

  def updateUserOrgRole(userWithRole: User.WithRole, orgId: UUID, userId: String)(implicit database: DB): Future[Int] = {
    val now = new Timestamp((new java.util.Date).getTime)

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
      action
    }
  }
}
