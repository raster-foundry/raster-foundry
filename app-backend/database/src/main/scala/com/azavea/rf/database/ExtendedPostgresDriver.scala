package com.azavea.rf.database

import com.azavea.rf.datamodel._
import geotrellis.slick.PostGisProjectionSupport
import com.github.tminglei.slickpg._
import scala.collection.immutable.Map
import spray.json._

/** Custom Postgres driver that adds custom column types and implicit conversions
  *
  * Adds support for the following column types
  *  - JSONB
  *  - JobStatus (enum)
  *  - Visibility (enum)
  *  - text[] (array text)
  */
trait ExtendedPostgresDriver extends ExPostgresDriver
    with PgArraySupport
    with PgSprayJsonSupport
    with PgEnumSupport
    with PostGisProjectionSupport {

  override val pgjson = "jsonb"

  override val api = RFAPI

  // Implicit conversions to/from column types
  object RFAPI extends API
      with ArrayImplicits
      with JsonImplicits
      with RFDatabaseJsonProtocol
      with PostGISProjectionImplicits
      with PostGISProjectionAssistants {

    implicit val metadataMapper = MappedJdbcType.base[Map[String, Any], JsValue](_.toJson,
      _.convertTo[Map[String, Any]])
    implicit def strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)

    implicit val userRoleTypeMapper = createEnumJdbcType[User.Role]("UserRole", _.repr,
      User.Role.fromString, quoteName = false)
    implicit val userRoleTypeListMapper = createEnumListJdbcType[User.Role]("UserRole",
      _.repr, User.Role.fromString, quoteName = false)
    implicit val userRoleColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[User.Role]
    implicit val userRoleOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[User.Role]

    implicit val jobStatusTypeMapper = createEnumJdbcType[JobStatus]("JobStatus", _.repr,
      JobStatus.fromString, quoteName = false)
    implicit val jobStatusTypeListMapper = createEnumListJdbcType[JobStatus]("JobStatus",
      _.repr, JobStatus.fromString, quoteName = false)
    implicit val jobStatusColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[JobStatus]
    implicit val jobStatusOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[JobStatus]

    implicit val visibilityTypeMapper = createEnumJdbcType[Visibility]("Visibility", _.repr,
      Visibility.fromString, quoteName = false)
    implicit val visibilityTypeListMapper = createEnumListJdbcType[Visibility]("Visibility", _.repr,
      Visibility.fromString, quoteName = false)
    implicit val visibilityColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[Visibility]
    implicit val visibilityOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[Visibility]

    implicit val thumbnailDimTypeMapper = createEnumJdbcType[ThumbnailSize]("ThumbnailSize", _.toString,
      ThumbnailSize.fromString, quoteName = false)
    implicit val thumbnailDimTypeListMapper = createEnumListJdbcType[ThumbnailSize]("ThumbnailSize", _.toString,
      ThumbnailSize.fromString, quoteName = false)
    implicit val thumbnailDimColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[ThumbnailSize]
    implicit val thumbnailDimOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[ThumbnailSize]
  }
}

object ExtendedPostgresDriver extends ExtendedPostgresDriver
