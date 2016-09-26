package com.azavea.rf.user

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import scala.language.postfixOps
import java.sql.Timestamp
import slick.lifted.Query
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import com.azavea.rf.datamodel.latest.schema.tables.{Users, Organizations, UsersToOrganizations}
import com.azavea.rf.datamodel.latest.schema.tables.{UsersRow, UsersToOrganizationsRow}
import com.azavea.rf.utils.{Database, PaginatedResponse}
import com.azavea.rf.AkkaSystem


class UserErrorException(message: String = null, cause: Throwable = null)
    extends RuntimeException(UserErrorException.defaultMessage(message, cause), cause)


object UserErrorException {
  def defaultMessage(message: String, cause: Throwable) =
    if (message != null) message
    else if (cause != null) cause.toString()
    else null
}


/**
  * Instantiated from a POST request to create a user. Typically used with the default public org.
  */
case class UsersRowCreate(id: String, organizationId: java.util.UUID, role: Option[String] = None) {
  def toUsersOrgTuple(): (UsersRow, UsersToOrganizationsRow)= {
    val now = new Timestamp((new java.util.Date()).getTime())
    val newUUID = java.util.UUID.randomUUID
    val user = UsersRow(
      id=id
    )
    val defaultedRole = role match {
      case Some(UserRoles(role)) => role
      case Some(userOrgRoleJoins: String) =>
        throw new UserErrorException( "\"" + userOrgRoleJoins + "\" is not a valid user Role")
      case None => UserRoles.User
    }

    val userToOrg = UsersToOrganizationsRow(
      userId=id,
      organizationId=organizationId,
      defaultedRole.toString(),
      now,
      now
    )
    (user, userToOrg)
  }
}


case class OrganizationWithRole(id: java.util.UUID, name: String, role: String)


case class UserRoleOrgJoin(userId: String, orgId: java.util.UUID, orgName: String, userRole: String)


case class UserWithOrgs(id: String, organizations: Seq[OrganizationWithRole])


object UserService extends AkkaSystem.LoggerExecutor {

  /**
    * Recursively applies a list of sort parameters from a page request
    */
  def applySort(query: Query[Users,Users#TableElementType,Seq], sortMap: Map[String, Order])
               (implicit database: Database, ec: ExecutionContext):
      Query[Users,Users#TableElementType,Seq] = {
    import database.driver.api._

    log.debug(s"Returning users -- SQL: ${query.result.statements.headOption}")

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

  def joinUsersRolesOrgs(query: Query[Users, UsersRow, Seq])(implicit database: Database) = {
    import database.driver.api._

    log.debug(s"Performing Users/Org roles join -- SQL: ${query.result.statements.headOption}")

    val userOrgJoin = query join UsersToOrganizations join Organizations on {
      case ((user, userToOrg), org) =>
        user.id === userToOrg.userId &&
          userToOrg.organizationId === org.id
    }

    for {
      ((user, userToOrg), org) <- userOrgJoin
    } yield (user.id, org.id, org.name, userToOrg.role)
  }

  def groupByUserId(joins: Seq[UserRoleOrgJoin]): Seq[UserWithOrgs] = {
    joins.groupBy(_.userId).map {
      case (userId, joins) => UserWithOrgs(
        userId,
        joins.map(
          join => OrganizationWithRole(join.orgId, join.orgName, join.userRole)
        )
      )
    } toSeq
  }

  /**
    * Returns a paginated result with Users
    *
    * @param page page request that has limit, offset, and sort parameters
    */
  def getPaginatedUsers(page: PageRequest)(implicit database: Database, ec: ExecutionContext):
      Future[PaginatedResponse[UserWithOrgs]] = {
    import database.driver.api._

    val usersQueryAction = joinUsersRolesOrgs(
        applySort(Users, page.sort)
          .drop(page.offset * page.limit)
          .take(page.limit)
      ).result

    log.debug(s"Fetching users -- SQL: ${usersQueryAction.statements.headOption}")
    val usersQueryResult = database.db.run {
      usersQueryAction
    } map {
      joinTuples => joinTuples.map(joinTuple => UserRoleOrgJoin.tupled(joinTuple))
    } map {
      groupByUserId
    }

    val nUsersAction = Users.length.result
    log.debug(s"Counting users -- SQL: ${nUsersAction.statements.headOption}")
    val totalUsersResult = database.db.run {
      nUsersAction
    }

    for {
      totalUsers <- totalUsersResult
      users <- usersQueryResult
    } yield {
      val hasNext = (page.offset + 1) * page.limit < totalUsers // 0 indexed page offset
      val hasPrevious = page.offset > 0
      PaginatedResponse(totalUsers, hasPrevious, hasNext, page.offset, page.limit, users)
    }
  }

  def createUser(user: UsersRowCreate)(implicit database: Database, ec: ExecutionContext):
      Future[Try[UsersRow]] = {
    import database.driver.api._
    val(userRow, usersToOrganizationsRow) = user.toUsersOrgTuple()

    val insertAction = Users.forceInsert(userRow)
    val userToOrgAction = UsersToOrganizations.forceInsert(usersToOrganizationsRow)
    val userInsert = (
      for {
        u <- insertAction
        userToOrg <- userToOrgAction
      } yield ()
    ).transactionally

    log.debug(s"Inserting user -- User SQL: ${insertAction.statements.headOption}")
    log.debug(
      s"Inserting into User/Org join -- User/Org SQL: ${userToOrgAction.statements.headOption}"
    )

    database.db.run {
      userInsert.asTry
    } map {
      case Success(_) => Success(userRow)
      case Failure(e) => Failure(e)
    }
  }

  def createUserWithAuthId(sub: String)(implicit database: Database, ec: ExecutionContext):
      Future[Try[UsersRow]] = {
    import database.driver.api._

    val newUUID = java.util.UUID.randomUUID
    database.db.run {
      Organizations.filter(_.name === "Public").result.headOption
    } flatMap {
      case Some(org) => {
        val userCreate = UsersRowCreate(sub, org.id)
        createUser(userCreate)
      }
      case _ => Future(Failure(new Exception("No public org found in database")))
    }
  }

  def getUserById(id: String)(implicit database: Database): Future[Option[UsersRow]] = {
    import database.driver.api._

    val getUserAction = Users.filter(_.id === id).result
    log.debug(s"Attempting to retrieve user $id -- SQL: ${getUserAction.statements.headOption})")

    database.db.run {
      getUserAction.headOption
    }
  }

  def getUserWithOrgsById(id: String)(implicit database: Database, ec: ExecutionContext):
      Future[Option[UserWithOrgs]] = {
    import database.driver.api._

    database.db.run {
      joinUsersRolesOrgs(Users.filter(_.id === id)).result
    } map {
      joinTuples => joinTuples.map(joinTuple => UserRoleOrgJoin.tupled(joinTuple))
    } map {
      groupByUserId
    } map {
      _.headOption
    }

  }
}
