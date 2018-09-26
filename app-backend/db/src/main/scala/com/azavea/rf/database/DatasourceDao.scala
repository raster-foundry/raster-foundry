package com.azavea.rf.database

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PageRequest
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

object DatasourceDao
    extends Dao[Datasource]
    with ObjectPermissions[Datasource] {

  val tableName = "datasources"

  val selectF: Fragment = sql"""
      SELECT
        distinct(id), created_at, created_by, modified_at, modified_by, owner,
        name, visibility, composites, extras, bands, license_name
      FROM
    """ ++ tableF

  def unsafeGetDatasourceById(datasourceId: UUID): ConnectionIO[Datasource] =
    query.filter(datasourceId).select

  def getDatasourceById(datasourceId: UUID): ConnectionIO[Option[Datasource]] =
    query.filter(datasourceId).selectOption

  def listDatasources(page: PageRequest, params: DatasourceQueryParameters)
    : ConnectionIO[PaginatedResponse[Datasource]] = {
    DatasourceDao.query
      .filter(params)
      .page(page, fr"")
  }

  def create(datasource: Datasource, user: User): ConnectionIO[Datasource] = {
    val ownerId = util.Ownership.checkOwner(user, Some(datasource.owner))
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, modified_by, owner,
      name, visibility, composites, extras, bands, license_name)
    VALUES
      (${datasource.id}, ${datasource.createdAt}, ${datasource.createdBy}, ${datasource.modifiedAt},
      ${datasource.modifiedBy}, ${ownerId}, ${datasource.name},
      ${datasource.visibility}, ${datasource.composites},
      ${datasource.extras}, ${datasource.bands}, ${datasource.licenseName})
    """).update.withUniqueGeneratedKeys[Datasource](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "owner",
      "name",
      "visibility",
      "composites",
      "extras",
      "bands",
      "license_name"
    )
  }

  def updateDatasource(datasource: Datasource,
                       id: UUID,
                       user: User): ConnectionIO[Int] = {
    // fetch datasource so we can check if user is allowed to update (access control)
    val now = new Timestamp(new java.util.Date().getTime)
    val updateQuery =
      fr"UPDATE" ++ this.tableF ++ fr"SET" ++
        fr"""
      modified_at = ${now},
      modified_by = ${user.id},
      name = ${datasource.name},
      visibility = ${datasource.visibility},
      composites = ${datasource.composites},
      extras = ${datasource.extras},
      bands = ${datasource.bands},
      license_name = ${datasource.licenseName}
      where id = ${id}
      """
    updateQuery.update.run
  }

  def deleteDatasource(id: UUID): ConnectionIO[Int] = {
    (fr"DELETE FROM " ++ this.tableF ++ fr"WHERE id = ${id}").update.run
  }

  def createDatasource(dsCreate: Datasource.Create,
                       user: User): ConnectionIO[Datasource] = {
    val datasource = dsCreate.toDatasource(user)
    this.create(datasource, user)
  }

  def isDeletable(datasourceId: UUID,
                  user: User,
                  objectType: ObjectType): ConnectionIO[Boolean] = {
    val statusF: List[Fragment] =
      List("CREATED", "UPLOADING", "UPLOADED", "QUEUED", "PROCESSING")
        .map(status => fr"upload_status = ${status}::upload_status")

    for {
      datasourceO <- DatasourceDao.getDatasourceById(datasourceId)
      isOwner = datasourceO match {
        case Some(datasource) if datasource.owner == user.id => true
        case _                                               => false
      }
      isShared <- DatasourceDao.query
        .filter(datasourceId)
        .filter(fr"acrs <> '{}':text[]")
        .exists
      hasUpload <- UploadDao.query
        .filter(fr"datasource = ${datasourceId}")
        .filter(fr"(" ++ Fragments.or(statusF: _*) ++ fr")")
        .exists
    } yield isOwner && !isShared && !hasUpload
  }

  def deleteDatasourceWithRelated(
      datasourceId: UUID): ConnectionIO[List[Int]] = {
    for {
      uDeleteCount <- UploadDao.query
        .filter(fr"datasource = ${datasourceId}")
        .delete
      sDeleteCount <- SceneDao.query
        .filter(fr"datasource = ${datasourceId}")
        .delete
      dDeleteCount <- DatasourceDao.query.filter(datasourceId).delete
    } yield { List(uDeleteCount, sDeleteCount, dDeleteCount) }
  }

  def authQuery(user: User,
                objectType: ObjectType,
                ownershipTypeO: Option[String] = None,
                groupTypeO: Option[GroupType] = None,
                groupIdO: Option[UUID] = None): Dao.QueryBuilder[Datasource] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Datasource](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Datasource](selectF,
                                     tableF,
                                     List(
                                       queryObjectsF(user,
                                                     objectType,
                                                     ActionType.View,
                                                     ownershipTypeO,
                                                     groupTypeO,
                                                     groupIdO)))
    }

  def authorized(user: User,
                 objectType: ObjectType,
                 objectId: UUID,
                 actionType: ActionType): ConnectionIO[Boolean] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .exists
}
