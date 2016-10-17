package com.azavea.rf.database.tables

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import com.typesafe.scalalogging.LazyLogging
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Users(_tableTag: Tag) extends Table[User](_tableTag, "users") {
  def * = id <> (User.apply, User.unapply)
  /** Maps whole row to an option. Useful for outer joins. */
  def ? = Rep.Some(id).shaped.<>(r => r.map(_=> User(r.get)), (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val id: Rep[String] = column[String]("id", O.PrimaryKey, O.Length(255,varying=true))
}

object Users extends TableQuery(tag => new Users(tag)) with LazyLogging {
  type TableQuery = Query[Users,Users#TableElementType, Seq]

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

  def joinUsersRolesOrgs(query: Query[Users, User, Seq])(implicit database: DB) = {
    logger.debug(s"Performing Users/Org roles join -- SQL: ${query.result.statements.headOption}")

    val userOrgJoin = query join UsersToOrganizations join Organizations on {
      case ((user, userToOrg), org) =>
        user.id === userToOrg.userId &&
          userToOrg.organizationId === org.id
    }

    for {
      ((user, userToOrg), org) <- userOrgJoin
    } yield (user.id, org.id, org.name, userToOrg.role)
  }

  def groupByUserId(joins: Seq[User.RoleOrgJoin]): Seq[User.WithOrgs] =
    joins.groupBy(_.userId).map {
      case (userId, joins) => User.WithOrgs(userId, joins.map(join => Organization.WithRole(join.orgId, join.orgName, join.userRole)))
    }.toSeq

  /**
    * Returns a paginated result with Users
    *
    * @param page page request that has limit, offset, and sort parameters
    */
  def getPaginatedUsers(page: PageRequest)(implicit database: DB):
      Future[PaginatedResponse[User.WithOrgs]] = {

    val usersQueryAction = joinUsersRolesOrgs(
        applySort(Users, page.sort)
          .drop(page.offset * page.limit)
          .take(page.limit)
      ).result

    logger.debug(s"Fetching users -- SQL: ${usersQueryAction.statements.headOption}")
    val usersQueryResult = database.db.run {
      usersQueryAction
    } map {
      joinTuples => joinTuples.map(joinTuple => User.RoleOrgJoin.tupled(joinTuple))
    } map {
      groupByUserId
    }

    val nUsersAction = Users.length.result
    logger.debug(s"Counting users -- SQL: ${nUsersAction.statements.headOption}")
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

  def createUser(user: User.Create)(implicit database: DB):  Future[User] = {
    val(userRow, usersToOrganizationsRow) = user.toUsersOrgTuple()

    val insertAction = Users.forceInsert(userRow)
    val userToOrgAction = UsersToOrganizations.forceInsert(usersToOrganizationsRow)
    val userInsert = (
      for {
        u <- insertAction
        userToOrg <- userToOrgAction
      } yield ()
    ).transactionally

    logger.debug(s"Inserting user -- User SQL: ${insertAction.statements.headOption}")
    logger.debug(
      s"Inserting into User/Org join -- User/Org SQL: ${userToOrgAction.statements.headOption}"
    )

    database.db.run {
      userInsert.map(_ => userRow)
    }
  }

  def createUserWithAuthId(sub: String)(implicit database: DB): Future[User] = {

    database.db.run {
      Organizations.filter(_.name === "Public").result.headOption
    } flatMap {
      case Some(org) =>
        createUser(User.Create(sub, org.id))
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

  def getUserWithOrgsById(userId: String)(implicit database: DB): Future[Option[User.WithOrgs]] = {
    database.db.run {
      joinUsersRolesOrgs(Users.filter(_.id === userId)).result
    } map {
      joinTuples => joinTuples.map(joinTuple => User.RoleOrgJoin.tupled(joinTuple))
    } map {
      groupByUserId
    } map {
      _.headOption
    }

  }
}
