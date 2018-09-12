package com.azavea.rf.common

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging

import com.amazonaws.services.batch.AWSBatchClientBuilder
import com.amazonaws.services.batch.model.SubmitJobRequest

import scala.collection.immutable.Map
import scala.collection.JavaConverters._

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

    logger.info(s"Using ${awsbatchConfig.environment} in AWS Batch")

    val runBatch: Boolean = {
      awsbatchConfig.environment
        .toLowerCase() == "staging" || awsbatchConfig.environment
        .toLowerCase() == "production"
    }

    if (runBatch) {
      logger.info(s"Trying to submit job: ${jobName}")
      try {
        val submitJobResult = batchClient.submitJob(jobRequest)
        logger.info(s"Submit Job Result: ${submitJobResult}")
      } catch {
        case e: Exception =>
          logger.error(
            s"There was an error submitting ${jobName}; Exception: ${e.getLocalizedMessage}"
          )
          throw e
      }
    } else {
      logger.warn(
        s"Not submitting AWS Batch -- not in production or staging, in ${awsbatchConfig.environment}")
      logger.warn(
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

  def kickoffProjectExport(exportId: UUID): Unit = {
    val jobDefinition = awsbatchConfig.exportJobName
    val jobName = s"$jobDefinition-$exportId"
    submitJobRequest(jobDefinition,
                     awsbatchConfig.ingestJobQueue,
                     Map("exportId" -> s"$exportId"),
                     jobName)
  }

  def kickoffAOIUpdateProject(projectId: UUID): Unit = {
    val jobDefinition = awsbatchConfig.aoiUpdateJobName
    val jobName = s"$jobDefinition-$projectId"
    submitJobRequest(jobDefinition,
                     awsbatchConfig.jobQueue,
                     Map("projectId" -> s"$projectId"),
                     jobName)
  }
}
