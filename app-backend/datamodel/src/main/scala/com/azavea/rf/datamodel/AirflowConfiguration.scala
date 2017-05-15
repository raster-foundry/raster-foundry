package com.azavea.rf.datamodel

import java.util.UUID

import io.circe.syntax._
import io.circe.Encoder
import io.circe.generic.JsonCodec


/** Airflow config submitted for various jobs */
sealed trait AirflowConfiguration

@JsonCodec
case class SceneIngestConfiguration(
  sceneId: UUID
) extends AirflowConfiguration

@JsonCodec
case class SceneImportConfiguration(
  uploadId: UUID
) extends AirflowConfiguration

@JsonCodec
case class ProjectExportConfiguration(
  exportId: UUID
) extends AirflowConfiguration

object AirflowConfiguration {
  implicit val encodeAirflowConfig: Encoder[AirflowConfiguration] = Encoder.instance {
    case sceneIngest @ SceneIngestConfiguration(_) => sceneIngest.asJson
    case sceneImport @ SceneImportConfiguration(_) => sceneImport.asJson
    case projectExport @ ProjectExportConfiguration(_) => projectExport.asJson
  }
}

