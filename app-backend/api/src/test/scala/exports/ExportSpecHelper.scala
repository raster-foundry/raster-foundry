package com.azavea.rf.api.exports

import com.azavea.rf.api.project.ProjectSpecHelper
import com.azavea.rf.datamodel.{Export, ExportOptions, ExportStatus, ExportType, Visibility}

import io.circe.syntax._

import java.net.URI
import java.util.UUID

trait ExportSpecHelper { self: ProjectSpecHelper =>
  val baseExport = "/api/exports/"
  val sceneId = UUID.fromString("697a0b91-b7a8-446e-842c-97cda155554d")
  val randomSceneId = UUID.randomUUID()
  var projectId = ""

  var exportId = ""
  lazy val export = Export.Create(
    organizationId = publicOrgId,
    projectId = Some(UUID.fromString(projectId)),
    exportStatus = ExportStatus.ToBeExported,
    exportType = ExportType.Local,
    visibility = Visibility.Public,
    owner = None,
    toolRunId = None,
    exportOptions = ExportOptions(
      mask = None,
      resolution = 2,
      stitch = false,
      crop = false,
      raw = false,
      bands = None,
      rasterSize = None,
      crs = None,
      source = new URI("s3://test"),
      operation = "id"
    ).asJson
  )

  var insertedExport: Export = null
  lazy val updatedExport = insertedExport.copy(exportStatus = ExportStatus.Exported)
}
