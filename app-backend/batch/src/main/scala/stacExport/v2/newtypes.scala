package com.rasterfoundry.batch.stacExport.v2

import com.azavea.stac4s.StacItem
import io.circe.Json
import io.estatico.newtype.macros.newtype

import java.util.UUID

@SuppressWarnings(Array("AsInstanceOf"))
object newtypes {
  @newtype case class AnnotationProjectId(value: UUID)
  @newtype case class SceneItem(value: StacItem)
  @newtype case class LabelItem(value: StacItem)
  @newtype case class TaskGeoJSON(value: Json)
  @newtype case class S3URL(value: String)
}
