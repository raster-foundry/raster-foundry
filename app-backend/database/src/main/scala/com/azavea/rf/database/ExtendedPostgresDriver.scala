package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast._

import geotrellis.slick.PostGisProjectionSupport
import com.github.tminglei.slickpg._
import io.circe._
import io.circe.syntax._

/** Custom Postgres driver that adds custom column types and implicit conversions
  *
  * Adds support for the following column types
  *  - IngestStatus
  *  - JSONB
  *  - JobStatus (enum)
  *  - Visibility (enum)
  *  - text[] (array text)
  */
trait ExtendedPostgresDriver extends ExPostgresDriver
  with PgArraySupport
  with PgRangeSupport
  with PgCirceJsonSupport
  with PgEnumSupport
  with PostGisProjectionSupport {

  override val pgjson = "jsonb"

  override val api = RFAPI

  // Implicit conversions to/from column types
  object RFAPI extends API
    with ArrayImplicits
    with RangeImplicits
    with CirceImplicits
    with PostGISProjectionImplicits
    with PostGISProjectionAssistants {

    implicit def strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)

    implicit val colorCorrectParamsMapper = MappedJdbcType.base[ColorCorrect.Params, Json](_.asJson,
      _.as[ColorCorrect.Params] match {
        case Right(ast) => ast
        case Left(e) => throw e
      })

    implicit val singleBandOptionsParamsMapper = MappedJdbcType.base[SingleBandOptions.Params, Json](_.asJson,
      _.as[SingleBandOptions.Params] match {
        case Right(ast) => ast
        case Left(e) => throw e
      })

    implicit val userRoleTypeMapper = createEnumJdbcType[UserRole]("UserRole", _.repr,
      UserRole.fromString, quoteName = false)
    implicit val userRoleTypeListMapper = createEnumListJdbcType[UserRole]("UserRole",
      _.repr, UserRole.fromString, quoteName = false)
    implicit val userRoleColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[UserRole]
    implicit val userRoleOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[UserRole]

    implicit val ingestStatusTypeMapper = createEnumJdbcType[IngestStatus](
      "IngestStatus", _.repr,
      IngestStatus.fromString, quoteName = false
    )
    implicit val ingestStatusTypeListMapper = createEnumListJdbcType[IngestStatus](
      "IngestStatus",
      _.repr, IngestStatus.fromString, quoteName = false
    )
    implicit val ingestStatusColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[IngestStatus]
    implicit val ingestStatusOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[IngestStatus]

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

    implicit val uploadStatusTypeMapper = createEnumJdbcType[UploadStatus]("UploadStatus", _.repr,
      UploadStatus.fromString, quoteName = false)
    implicit val uploadStatusTypeListMapper = createEnumListJdbcType[UploadStatus]("UploadStatus", _.repr,
      UploadStatus.fromString, quoteName = false)
    implicit val uploadStatusColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[UploadStatus]
    implicit val uploadStatusOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[UploadStatus]

    implicit val exportStatusTypeMapper = createEnumJdbcType[ExportStatus]("ExportStatus", _.repr,
      ExportStatus.fromString, quoteName = false)
    implicit val exportStatusTypeListMapper = createEnumListJdbcType[ExportStatus]("ExportStatus", _.repr,
      ExportStatus.fromString, quoteName = false)
    implicit val exportStatusColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[ExportStatus]
    implicit val exportStatusOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[ExportStatus]

    implicit val fileTypeMapper = createEnumJdbcType[FileType]("FileType", _.repr,
      FileType.fromString, quoteName = false)
    implicit val fileTypeListMapper = createEnumListJdbcType[FileType]("FileType", _.repr,
      FileType.fromString, quoteName = false)
    implicit val fileTypeColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[FileType]
    implicit val fileTypeOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[FileType]

    implicit val uploadTypeTypeMapper = createEnumJdbcType[UploadType]("UploadType", _.repr,
      UploadType.fromString, quoteName = false)
    implicit val uploadTypeTypeListMapper = createEnumListJdbcType[UploadType]("UploadType", _.repr,
      UploadType.fromString, quoteName = false)
    implicit val uploadTypeColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[UploadType]
    implicit val uploadTypeOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[UploadType]

    implicit val exportTypeTypeMapper = createEnumJdbcType[ExportType]("ExportType", _.repr,
      ExportType.fromString, quoteName = false)
    implicit val exportTypeTypeListMapper = createEnumListJdbcType[ExportType]("ExportType", _.repr,
      ExportType.fromString, quoteName = false)
    implicit val exportTypeColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder[ExportType]
    implicit val exportTypeOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder[ExportType]

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
