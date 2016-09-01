package com.azavea.rf.user

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import java.sql.Timestamp
import slick.lifted.{ColumnOrdered, TableQuery, Query}
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import de.choffmeister.auth.common.JsonWebToken

import com.azavea.rf.datamodel.latest.schema.tables.{Users, Organizations}
import com.azavea.rf.datamodel.latest.schema.tables.UsersRow
import com.azavea.rf.utils.{Database,PaginatedResponse}


/**
  * Instantiated from a POST request to create a user
  */
case class UsersRowCreate(authId: String, organizationId: java.util.UUID) {
  def toUsersRow(): UsersRow = {
    val now = new Timestamp((new java.util.Date()).getTime())
    val newUUID = java.util.UUID.randomUUID
    UsersRow(
      id=newUUID,
      authId=authId,
      organizationId=organizationId
    )
  }
}


object UserService {

  def getUsers()(implicit database: Database): Future[Seq[UsersRow]]= {
    import database.driver.api._
    database.db.run {
      Users.result
    }
  }

  /**
    * Recursively applies a list of sort parameters from a page request
    */
  def applySort(query: Query[Users,Users#TableElementType,Seq], sortMap: Map[String, Order])
               (implicit database: Database, ec: ExecutionContext):
      Query[Users,Users#TableElementType,Seq] = {
    import database.driver.api._

    sortMap.headOption match {
      case Some(("id", order)) =>
        order match {
          case Order.Asc => applySort(query.sortBy(_.id.asc), sortMap.tail)
          case Order.Desc => applySort(query.sortBy(_.id.desc), sortMap.tail)
        }
      case Some(("organizationId", order)) =>
        order match {
          case Order.Asc => applySort(query.sortBy(_.organizationId.asc), sortMap.tail)
          case Order.Desc => applySort(query.sortBy(_.organizationId.desc), sortMap.tail)
        }
      case Some((_, order)) => applySort(query, sortMap.tail)
      case _ => query
    }
  }

  /**
    * Returns a paginated result with Users
    *
    * @param page page request that has limit, offset, and sort parameters
    */
  def getPaginatedUsers(page: PageRequest)(implicit database: Database, ec: ExecutionContext):
      Future[PaginatedResponse[UsersRow]] = {
    import database.driver.api._

    val usersQuery = database.db.run {
      applySort(Users, page.sort)
        .drop(page.offset * page.limit)
        .take(page.limit)
        .result
    }

    val totalUsersQuery = database.db.run {
      Users.length.result
    }

    for {
      totalUsers <- totalUsersQuery
      users <- usersQuery
    } yield {
      val hasNext = (page.offset + 1) * page.limit < totalUsers // 0 indexed page offset
      val hasPrevious = page.offset > 0
      PaginatedResponse(totalUsers, hasPrevious, hasNext, page.offset, page.limit, users)
    }
  }

  def createUser(user: UsersRowCreate)(implicit database: Database, ec: ExecutionContext):
      Future[Try[UsersRow]] = {
    import database.driver.api._
    val userRow = user.toUsersRow()

    database.db.run {
      Users.forceInsert(userRow).asTry
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
        val userRow = UsersRowCreate(sub, org.id)
        createUser(userRow)
      }
      case _ => Future(Failure(new Exception("No public org found in database")))
    }
  }

  def getUserById(id: java.util.UUID)(implicit database: Database): Future[Option[UsersRow]] = {
    import database.driver.api._

    database.db.run {
      Users.filter(_.id === id).result.headOption
    }
  }

  def getUserByAuthId(id: String)(implicit database: Database):
      Future[Option[UsersRow]] = {
    import database.driver.api._

    database.db.run {
      Users.filter(_.authId === id).result.headOption
    }
  }

  def updateUser(user: UsersRow, id: java.util.UUID)(implicit database: Database): Future[Try[Int]] = {
    import database.driver.api._
    val now = new Timestamp((new java.util.Date()).getTime())

    // ignores id if it's changed in the request
    // TODO throw exception if ignored attributes are changed
    val query = for { u <- Users if u.id === id} yield (
      u.organizationId
    )
    // TODO catch exception when uniqueness constraint on email fails
    // should return 400 bad request : email is already in use
    database.db.run {
      query.update(
        (
          user.organizationId
        )
      ).asTry
    }
  }
}
