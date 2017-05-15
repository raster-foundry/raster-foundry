package com.azavea.rf.common

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, _}
import akka.stream.Materializer
import com.azavea.rf.datamodel.{AirflowConfiguration, ProjectExportConfiguration, SceneImportConfiguration, SceneIngestConfiguration}
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax._

import scala.concurrent.ExecutionContext


/** Submits airflow jobs in a fire-and-forget manner; logs/reports errors */
trait Airflow extends RollbarNotifier with LazyLogging {

  implicit val system: ActorSystem
  implicit val materializer: Materializer

  val airflowConfig = Config.airflow

  def triggerDag(dag: AirflowConfiguration, dagName: String)(implicit ec: ExecutionContext): Unit = {
    Http().singleRequest(
      HttpRequest(
        method = POST,
        uri = s"${airflowConfig.baseUrl}/api/dag-runs/${dagName}",
        entity = HttpEntity(`application/json`, dag.asJson.noSpaces)
      )
    ).map { response =>
      if(response.status.isSuccess){
        logger.info(s"Successfully kick off ${dagName} for ${dag}")
      } else{
        val s = s"Failed to kick off dag ${dagName} for ${dag}"
        logger.error(s)
        sendError(s)
      }
      response.discardEntityBytes()
    }
  }

  def kickoffSceneIngest(sceneId: UUID)(implicit ec: ExecutionContext): Unit = {
    val sceneIngestConf = SceneIngestConfiguration(sceneId)
    triggerDag(sceneIngestConf, "ingest_scene")
  }

  def kickoffSceneImport(uploadId: UUID)(implicit ec: ExecutionContext): Unit = {
    val sceneImportConf = SceneImportConfiguration(uploadId)
    triggerDag(sceneImportConf, "process_upload")
  }

  def kickoffProjectExport(exportId: UUID)(implicit ec: ExecutionContext): Unit = {
    val exportConf = ProjectExportConfiguration(exportId)
    triggerDag(exportConf, "export_project")
  }
}
