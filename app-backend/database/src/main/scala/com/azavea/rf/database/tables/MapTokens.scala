package com.azavea.rf.database.tables

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields._
import com.azavea.rf.database.query.{CombinedMapTokenQueryParameters, ListQueryResult}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction

/** Tokens associated with projects for sharing purposes
  */
class MapTokens(_tableTag: Tag) extends Table[MapToken](_tableTag, "map_tokens")
  with NameField
  with TimestampFields
  with OrganizationFkFields
  with UserFkFields
{
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, owner, organizationId, name, projectId, toolRunId) <> (
    MapToken.tupled, MapToken.unapply
  )

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val name: Rep[String] = column[String]("name")
  val projectId: Rep[Option[java.util.UUID]] = column[Option[java.util.UUID]]("project_id")
  val toolRunId: Rep[Option[java.util.UUID]] = column[Option[java.util.UUID]]("toolrun_id")

  lazy val organizationsFk = foreignKey("map_tokens_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val toolRunFk = foreignKey("map_tokens_toolrun_id_fkey", toolRunId, ToolRuns)(r => r.id.?, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val createdByUserFK = foreignKey("map_tokens_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("map_tokens_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val projectsFk = foreignKey("map_tokens_project_id_fkey", projectId, Projects)(r => r.id.?, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val ownerUserFK = foreignKey("datasources_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object MapTokens extends TableQuery(tag => new MapTokens(tag)) with LazyLogging {

  val tq = TableQuery[MapTokens]
  type TableQuery = Query[MapTokens, MapToken, Seq]

  implicit class withMapTokensTableQuery[M, U, C[_]](mapTokens: MapTokens.TableQuery) extends
    MapTokensTableQuery[M, U, C](mapTokens)

  def getMapToken(mapTokenId: UUID, user: User)(implicit database: DB): DBIO[Option[MapToken]]= {
    MapTokens
      .filterToOwnerIfNotInRootOrganization(user)
      .filter(_.id === mapTokenId)
      .result
      .headOption
  }

  def getMapTokenForTool(mapTokenId: UUID, toolRunId: UUID)(implicit database: DB): DBIO[Option[MapToken]]= {
    MapTokens
      .filter(_.id === mapTokenId)
      .filter(_.toolRunId === toolRunId)
      .result
      .headOption
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
      .filterToOwnerIfNotInRootOrganization(user)
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
    val mapToken = mapTokenCreate.toMapToken(user)
    (MapTokens returning MapTokens).forceInsert(mapToken)
  }

  def deleteMapToken(mapTokenId: UUID, user: User): DBIO[Int] = {
    MapTokens
      .filterToOwnerIfNotInRootOrganization(user)
      .filter(_.id === mapTokenId)
      .delete
  }

  def updateMapToken(mapToken: MapToken, mapTokenId: UUID, user: User) = {
    val updateTime = new Timestamp((new java.util.Date).getTime)
    val updateMapTokenQuery = for {
      updateMapToken <- MapTokens
                          .filterToOwnerIfNotInRootOrganization(user)
                          .filter(_.id === mapTokenId)
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
    mapTokens
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }

  def filterByProject(projectId: Option[UUID]): MapTokens.TableQuery = {
    projectId match {
      case Some(id) => mapTokens.filter(_.projectId === id)
      case _ => mapTokens
    }
  }

  def filterByName(name: Option[String]): MapTokens.TableQuery = {
    name match {
      case Some(s) => mapTokens.filter(_.name === s)
      case _ => mapTokens
    }
  }
}
