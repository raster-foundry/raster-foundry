package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.Cache
import com.rasterfoundry.datamodel.PageRequest
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._
import scalacache.CatsEffect.modes._
import scalacache._

import scala.concurrent.duration._

import java.sql.Timestamp
import java.util.UUID

object DatasourceDao
    extends Dao[Datasource]
    with ObjectPermissions[Datasource] {

  val tableName = "datasources"

  val select: Fragment = sql"""
      SELECT
        datasources.id, datasources.created_at, datasources.created_by,
        datasources.modified_at, datasources.owner,
        datasources.name, datasources.visibility,
        datasources.composites, datasources.extras, datasources.bands,
        datasources.license_name
      FROM
"""
  import Cache.DatasourceCache._

  val selectF: Fragment = select ++ tableF

  def deleteCache(id: UUID): ConnectionIO[Unit] = {
    for {
      _ <- remove(Datasource.cacheKey(id))(datasourceCache, async[ConnectionIO]).attempt
    } yield ()
  }

  def unsafeGetDatasourceById(datasourceId: UUID): ConnectionIO[Datasource] =
    cachingF(Datasource.cacheKey(datasourceId))(Some(30 minutes)) {
      query.filter(datasourceId).select
    }

  def getDatasourceById(datasourceId: UUID): ConnectionIO[Option[Datasource]] =
    Cache.getOptionCache(Datasource.cacheKey(datasourceId), Some(30 minutes)) {
      query.filter(datasourceId).selectOption
    }

  def listDatasources(
      page: PageRequest,
      params: DatasourceQueryParameters
  ): ConnectionIO[PaginatedResponse[Datasource]] = {
    DatasourceDao.query
      .filter(params)
      .page(page)
  }

  def create(datasource: Datasource, user: User): ConnectionIO[Datasource] = {
    val ownerId = util.Ownership.checkOwner(user, Some(datasource.owner))
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, owner,
      name, visibility, composites, extras, bands, license_name)
    VALUES
      (${datasource.id}, ${datasource.createdAt}, ${datasource.createdBy}, ${datasource.modifiedAt},
      ${ownerId}, ${datasource.name},
      ${datasource.visibility}, ${datasource.composites},
      ${datasource.extras}, ${datasource.bands}, ${datasource.licenseName})
    """).update.withUniqueGeneratedKeys[Datasource](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "owner",
      "name",
      "visibility",
      "composites",
      "extras",
      "bands",
      "license_name"
    )
  }

  def updateDatasource(
      datasource: Datasource,
      id: UUID
  ): ConnectionIO[Int] = {
    // fetch datasource so we can check if user is allowed to update (access control)
    val now = new Timestamp(new java.util.Date().getTime)
    val updateQuery =
      fr"UPDATE" ++ this.tableF ++ fr"SET" ++
        fr"""
      modified_at = ${now},
      name = ${datasource.name},
      visibility = ${datasource.visibility},
      composites = ${datasource.composites},
      extras = ${datasource.extras},
      bands = ${datasource.bands},
      license_name = ${datasource.licenseName}
      where id = ${id}
      """
    for {
      result <- updateQuery.update.run
      _ <- deleteCache(id)
    } yield result
  }

  def deleteDatasource(id: UUID): ConnectionIO[Int] = {
    (fr"DELETE FROM " ++ this.tableF ++ fr"WHERE id = ${id}").update.run
  }

  def createDatasource(
      dsCreate: Datasource.Create,
      user: User
  ): ConnectionIO[Datasource] = {
    val datasource = dsCreate.toDatasource(user)
    this.create(datasource, user)
  }

  def isDeletable(datasourceId: UUID, user: User): ConnectionIO[Boolean] = {
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
        .filter(fr"array_length(acrs, 1) is not null")
        .exists
      hasUpload <- UploadDao.query
        .filter(fr"datasource = ${datasourceId}")
        .filter(fr"(" ++ Fragments.or(statusF: _*) ++ fr")")
        .exists
    } yield isOwner && !isShared && !hasUpload
  }

  def deleteDatasourceWithRelated(
      datasourceId: UUID
  ): ConnectionIO[List[Int]] = {
    for {
      uDeleteCount <- UploadDao.query
        .filter(fr"datasource = ${datasourceId}")
        .delete
      sceneDeleteCount <- SceneDao.query
        .filter(fr"datasource = ${datasourceId}")
        .delete
      datasourceDeleteCount <- DatasourceDao.query.filter(datasourceId).delete
      _ <- deleteCache(datasourceId)
    } yield { List(uDeleteCount, sceneDeleteCount, datasourceDeleteCount) }
  }

  def getSceneDatasource(sceneId: UUID): ConnectionIO[Option[Datasource]] = {
    val joinTableF =
      fr"datasources join scenes on datasources.id = scenes.datasource"
    Cache.getOptionCache(s"Scene:$sceneId:Datasource", Some(10 minutes)) {
      Dao
        .QueryBuilder[Datasource](
          select ++ joinTableF,
          tableF,
          Nil
        )
        .filter(fr"scenes.id = $sceneId")
        .selectOption
    }
  }

  def authQuery(
      user: User,
      objectType: ObjectType,
      ownershipTypeO: Option[String] = None,
      groupTypeO: Option[GroupType] = None,
      groupIdO: Option[UUID] = None
  ): Dao.QueryBuilder[Datasource] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Datasource](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Datasource](
          selectF,
          tableF,
          List(
            queryObjectsF(
              user,
              objectType,
              ActionType.View,
              ownershipTypeO,
              groupTypeO,
              groupIdO
            )
          )
        )
    }

  def authorized(
      user: User,
      objectType: ObjectType,
      objectId: UUID,
      actionType: ActionType
  ): ConnectionIO[AuthResult[Datasource]] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .selectOption
      .map(AuthResult.fromOption _)
}
