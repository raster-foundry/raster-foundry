package com.rasterfoundry.common

import com.amazonaws.services.batch.AWSBatchClientBuilder
import com.amazonaws.services.batch.model.SubmitJobRequest
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._
import scala.collection.immutable.Map

import java.util.UUID

/** Submits jobs to AWS Batch for processing */
trait AWSBatch extends RollbarNotifier with LazyLogging {

  val awsbatchConfig = Config.awsbatch

  val batchClient = AWSBatchClientBuilder.defaultClient()

  @SuppressWarnings(Array("CatchException"))
  def submitJobRequest(jobDefinition: String,
                       jobQueueName: String,
                       parameters: Map[String, String],
                       jobName: String): Unit = {
    val jobRequest = new SubmitJobRequest()
      .withJobName(jobName)
      .withJobDefinition(jobDefinition)
      .withJobQueue(jobQueueName)
      .withParameters(parameters.asJava)

    logger.debug(s"Using ${awsbatchConfig.environment} in AWS Batch")

    val runBatch: Boolean = {
      awsbatchConfig.environment
        .toLowerCase() == "staging" || awsbatchConfig.environment
        .toLowerCase() == "production"
    }

    if (runBatch) {
      logger.debug(s"Trying to submit job: ${jobName}")
      try {
        val submitJobResult = batchClient.submitJob(jobRequest)
        logger.debug(s"Submit Job Result: ${submitJobResult}")
      } catch {
        case e: Exception =>
          logger.error(
            s"There was an error submitting ${jobName}; Exception: ${e.getLocalizedMessage}"
          )
          throw e
      }
    } else {
      logger.debug(
        s"Not submitting AWS Batch -- not in production or staging, in ${awsbatchConfig.environment}")
      logger.debug(
        s"Job Request: ${jobName} -- ${jobDefinition} -- ${parameters}")
    }

  }

  def kickoffSceneIngest(sceneId: UUID): Unit = {
    val jobDefinition = awsbatchConfig.ingestJobName
    val jobName = s"$jobDefinition-$sceneId"
    submitJobRequest(jobDefinition,
                     awsbatchConfig.ingestJobQueue,
                     Map("sceneId" -> s"$sceneId"),
                     jobName)
  }

  def kickoffSceneImport(uploadId: UUID): Unit = {
    val jobDefinition = awsbatchConfig.importJobName
    val jobName = s"$jobDefinition-$uploadId"
    submitJobRequest(jobDefinition,
                     awsbatchConfig.jobQueue,
                     Map("uploadId" -> s"$uploadId"),
                     jobName)
  }

  def kickoffGeojsonImport(uploadId: UUID): Unit = {
    val jobDefinition = awsbatchConfig.geojsonImportJobName
    val jobName = s"$jobDefinition-$uploadId"
    submitJobRequest(
      jobDefinition,
      awsbatchConfig.jobQueue,
      Map("uploadId" -> s"$uploadId"),
      jobName
    )
  }

  def kickoffProjectExport(exportId: UUID): Unit = {
    val jobDefinition = awsbatchConfig.exportJobName
    val jobName = s"$jobDefinition-$exportId"
    submitJobRequest(jobDefinition,
                     awsbatchConfig.ingestJobQueue,
                     Map("exportId" -> s"$exportId"),
                     jobName)
  }

  def kickoffStacExport(exportId: UUID): Unit = {
    val jobDefinition = awsbatchConfig.stacExportJobName
    val jobName = s"$jobDefinition-$exportId"
    submitJobRequest(jobDefinition,
                     awsbatchConfig.jobQueue,
                     Map("exportId" -> s"$exportId"),
                     jobName)
  }
}
