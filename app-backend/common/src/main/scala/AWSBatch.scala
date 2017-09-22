package com.azavea.rf.common

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging

import com.amazonaws.services.batch.AWSBatchClientBuilder
import com.amazonaws.services.batch.model.SubmitJobRequest

import scala.collection.immutable.Map
import scala.collection.JavaConversions._


/** Submits jobs to AWS Batch for processing */
trait AWSBatch extends RollbarNotifier with LazyLogging {

  val awsbatchConfig = Config.awsbatch

  val batchClient = AWSBatchClientBuilder.defaultClient()

  def submitJobRequest(jobDefinition: String, jobQueueName: String, parameters: Map[String, String]) = {
    val jobRequest = new SubmitJobRequest()
      .withJobName(jobDefinition)
      .withJobDefinition(jobDefinition)
      .withJobQueue(jobQueueName)
      .withParameters(parameters)

    val submitJobResult = batchClient.submitJob(jobRequest)

    logger.info("submit job result: {}", submitJobResult)
    submitJobResult
  }

  def kickoffSceneIngest(sceneId: UUID) = {
    submitJobRequest(awsbatchConfig.ingestJobName, awsbatchConfig.jobQueue, Map("sceneId" -> s"$sceneId"))
  }

  def kickoffSceneImport(uploadId: UUID) = {
    submitJobRequest(awsbatchConfig.importJobName, awsbatchConfig.jobQueue, Map("uploadId" -> s"$uploadId"))
  }

  def kickoffProjectExport(exportId: UUID) = {
    submitJobRequest(awsbatchConfig.exportJobName, awsbatchConfig.jobQueue, Map("exportId" -> s"$exportId"))
  }
}
