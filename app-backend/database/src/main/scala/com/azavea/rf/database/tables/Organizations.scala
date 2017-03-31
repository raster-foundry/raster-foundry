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

  def pageOrganizations(organizations: TableQuery, page: PageRequest, database: DB) = {

    val organizationsQueryResult = database.db.run {
      organizations
        .drop(page.offset * page.limit)
        .take(page.limit)
        .sort(page.sort)
        .result
    }
    val totalOrganizationsQuery = database.db.run {
      organizations.length.result
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

  def listOrganizations(page: PageRequest)(implicit database: DB) =
    pageOrganizations(Organizations, page, database)

  def listFilteredOrganizations(ids: Seq[UUID], page: PageRequest)(implicit database: DB) =
    pageOrganizations(Organizations.filter(_.id inSet ids), page, database)

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
}
