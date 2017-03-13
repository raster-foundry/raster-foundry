package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields._
import com.azavea.rf.database.query.{CombinedMapTokenQueryParameters, ListQueryResult, MapTokenQueryParameters}
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by cbrown on 3/11/17.
  */
class MapTokens(_tableTag: Tag) extends Table[MapToken](_tableTag, "map_tokens")
  with NameField
  with TimestampFields
  with OrganizationFkFields
  with UserFkFields
{
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, organizationId, name, projectId) <> (
    MapToken.tupled, MapToken.unapply
  )

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val name: Rep[String] = column[String]("name")
  val projectId: Rep[java.util.UUID] = column[java.util.UUID]("project_id")

  lazy val organizationsFk = foreignKey("map_tokens_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("map_tokens_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("map_tokens_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val projectsFk = foreignKey("map_tokens_project_id_fkey", projectId, Projects)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object MapTokens extends TableQuery(tag => new MapTokens(tag)) with LazyLogging {

  val tq = TableQuery[MapTokens]
  type TableQuery = Query[MapTokens, MapToken, Seq]

  implicit class withMapTokensTableQuery[M, U, C[_]](mapTokens: MapTokens.TableQuery) extends
    MapTokensTableQuery[M, U, C](mapTokens)

  def getMapToken(mapTokenId: UUID)(implicit database: DB): DBIO[Option[MapToken]]= {
    MapTokens.filter(_.id === mapTokenId).result.headOption
  }

  def validateMapToken(projectId: UUID, mapTokenId: UUID): DBIO[Int] = {
    MapTokens
      .filter(_.id === mapTokenId)
      .filter(_.projectId === projectId)
      .length
      .result
  }

  def listMapTokens(offset: Int, limit: Int, user: User, queryParameters: CombinedMapTokenQueryParameters):ListQueryResult[MapToken] = {
    val mapTokens = MapTokens
      .filterToOwner(user)
      .filterByProject(queryParameters.mapTokenParams.projectId)
      .filterByName(queryParameters.mapTokenParams.name)
      .filterByOrganization(queryParameters.orgParams)
      .filterByUser(queryParameters.userParams)
    ListQueryResult[MapToken](
      mapTokens
        .drop(limit * offset)
        .take(limit)
        .result,
      mapTokens.length.result
    )
  }

  def insertMapToken(mapTokenCreate: MapToken.Create, user: User): DBIO[MapToken] = {
    val mapToken = mapTokenCreate.toMapToken(user.id)
    (MapTokens returning MapTokens).forceInsert(mapToken)
  }

  def deleteMapToken(mapTokenId: UUID): DBIO[Int] = {
    MapTokens.filter(_.id === mapTokenId).delete
  }

  def updateMapToken(mapToken: MapToken, mapTokenId: UUID, user: User) = {
    val updateTime = new Timestamp((new java.util.Date).getTime)
    val updateMapTokenQuery = for {
      updateMapToken <- MapTokens.filter(_.id === mapTokenId).filterToOwner(user)
    } yield (
      updateMapToken.modifiedAt,
      updateMapToken.modifiedBy,
      updateMapToken.name
    )
    updateMapTokenQuery.update(
      updateTime,
      user.id,
      mapToken.name
    )
  }
}

class MapTokensTableQuery[M, U, C[_]](mapTokens: MapTokens.TableQuery) {
  def page(pageRequest: PageRequest): MapTokens.TableQuery = {
    MapTokens
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }

  def filterByProject(projectId: Option[UUID]): MapTokens.TableQuery = {
    projectId match {
      case Some(id) => MapTokens.filter(_.projectId === id)
      case _ => MapTokens
    }
  }

  def filterByName(name: Option[String]): MapTokens.TableQuery = {
    name match {
      case Some(s) => MapTokens.filter(_.name === s)
      case _ => MapTokens
    }
  }
}