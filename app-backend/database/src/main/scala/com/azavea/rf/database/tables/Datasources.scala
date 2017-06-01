package com.azavea.rf.database.tables

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields.{OrgFkVisibleFields, TimestampFields, UserFkVisibleFields, NameField}
import com.azavea.rf.database.query.{DatasourceQueryParameters, ListQueryResult}
import com.azavea.rf.datamodel._

import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import slick.model.ForeignKeyAction
import io.circe._

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/** Table that represents datasources
  *
  * Datasources can be user generated and help associate things like
  * tool compatibility and settings to imagery sources
  */
class Datasources(_tableTag: Tag) extends Table[Datasource](_tableTag, "datasources")
    with NameField
    with TimestampFields
    with UserFkVisibleFields
    with OrgFkVisibleFields
{
  def * = (id, createdAt, createdBy, modifiedAt, modifiedBy, owner, organizationId, name,
           visibility, composites, extras) <> (
    Datasource.tupled, Datasource.unapply
  )

  val id: Rep[java.util.UUID] = column[java.util.UUID]("id", O.PrimaryKey)
  val createdAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_at")
  val createdBy: Rep[String] = column[String]("created_by", O.Length(255,varying=true))
  val modifiedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("modified_at")
  val modifiedBy: Rep[String] = column[String]("modified_by", O.Length(255,varying=true))
  val owner: Rep[String] = column[String]("owner", O.Length(255,varying=true))
  val organizationId: Rep[java.util.UUID] = column[java.util.UUID]("organization_id")
  val name: Rep[String] = column[String]("name")
  val visibility: Rep[Visibility] = column[Visibility]("visibility")
  val composites: Rep[Json] = column[Json]("composites", O.Length(2147483647, varying=false))
  val extras: Rep[Json] = column[Json]("extras", O.Length(2147483647,varying=false))

  lazy val organizationsFk = foreignKey("datasources_organization_id_fkey", organizationId, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val createdByUserFK = foreignKey("datasources_created_by_fkey", createdBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val modifiedByUserFK = foreignKey("datasources_modified_by_fkey", modifiedBy, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val ownerUserFK = foreignKey("datasources_owner_fkey", owner, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

object Datasources extends TableQuery(tag => new Datasources(tag)) with LazyLogging {

  val tq = TableQuery[Datasources]
  type TableQuery = Query[Datasources, Datasource, Seq]


  implicit class withDatasourcesTableQuery[M, U, C[_]](datasources: Datasources.TableQuery) extends
    DatasourceTableQuery[M, U, C](datasources)

  /** List datasources given a page request
    *
    * @param pageRequest PageRequest information about sorting and page size
    */
  def listDatasources(offset: Int, limit: Int, datasourceParams: DatasourceQueryParameters, user: User) = {

    val dropRecords = limit * offset
    val accessibleDatasources = Datasources.filterUserVisibility(user)
    val datasourceFilterQuery = datasourceParams.name match {
      case Some(n) => accessibleDatasources.filter(_.name === n)
      case _ => accessibleDatasources
    }

    ListQueryResult[Datasource](
      (datasourceFilterQuery
         .drop(dropRecords)
         .take(limit)
         .result):DBIO[Seq[Datasource]],
      datasourceFilterQuery.length.result
    )
  }

  /** Insert a datasource given a create case class with a user
    *
    * @param datasourceToCreate Datasource.Create object to use to create full datasource
    * @param user               User to create a new datasource with
    */
  def insertDatasource(datasourceToCreate: Datasource.Create, user: User) = {
    val datasource = datasourceToCreate.toDatasource(user)
    (Datasources returning Datasources).forceInsert(datasource)
  }


  /** Given a datasource ID, attempt to retrieve it from the database
    *
    * @param datasourceId UUID ID of datasource to get from database
    * @param user         Results will be limited to user's organization
    */
  def getDatasource(datasourceId: UUID, user: User) =
    Datasources
      .filterUserVisibility(user)
      .filter(_.id === datasourceId)
      .result
      .headOption

  /** Given a datasource ID, attempt to remove it from the database
    *
    * @param datasourceId UUID ID of datasource to remove
    * @param user         Results will be limited to user's organization
    */
  def deleteDatasource(datasourceId: UUID, user: User) =
    Datasources
      .filterToSharedOrganizationIfNotInRoot(user)
      .filter(_.id === datasourceId)
      .delete

/** Update a datasource @param datasource Datasource to use for update
    * @param datasourceId UUID of datasource to update
    * @param user User to use to update datasource
    */
  def updateDatasource(datasource: Datasource, datasourceId: UUID, user: User) = {
    val updateTime = new Timestamp((new java.util.Date).getTime)

    val updateDatasourceQuery = for {
      updateDatasource <- Datasources
                            .filterToSharedOrganizationIfNotInRoot(user)
                            .filter(_.id === datasourceId)
    } yield (
      updateDatasource.modifiedAt,
      updateDatasource.modifiedBy,
      updateDatasource.organizationId,
      updateDatasource.name,
      updateDatasource.visibility,
      updateDatasource.composites,
      updateDatasource.extras
    )

    updateDatasourceQuery.update(
      updateTime,
      user.id,
      datasource.organizationId,
      datasource.name,
      datasource.visibility,
      datasource.composites,
      datasource.extras
    )
  }
}

class DatasourceTableQuery[M, U, C[_]](datasources: Datasources.TableQuery) {
  def page(pageRequest: PageRequest): Datasources.TableQuery = {
    Datasources
      .drop(pageRequest.offset * pageRequest.limit)
      .take(pageRequest.limit)
  }
}
