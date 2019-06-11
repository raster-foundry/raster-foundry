package com.rasterfoundry.common

import com.typesafe.scalalogging.LazyLogging
import com.amazonaws.services.lambda.AWSLambdaClientBuilder
import com.amazonaws.services.lambda.{AWSLambda => AwsLambda}
import com.amazonaws.services.lambda.model.{InvokeRequest, InvokeResult}
import io.circe.syntax._
import java.util.UUID

import com.rasterfoundry.datamodel.OverviewInput

/** Invoke AWS Lambda Functions */
trait AWSLambda extends RollbarNotifier with LazyLogging {

  val lambdaConfig = Config.awslambda
  val auth0Config = Config.auth0
  val s3Config = Config.s3

  val lambdaClient: AwsLambda = AWSLambdaClientBuilder.standard().build()

  val runLambda: Boolean = {
    lambdaConfig.environment
      .toLowerCase() == "staging" || lambdaConfig.environment
      .toLowerCase() == "production"
  }

  /**
    * Invoke lambda function with given arguments
    *
    * @param functionName the name of lambda function
    * @param invocationType enum of invocation type: RequestResponse (default), Event, DryRun
    * @param logType enum of log type: None, Tail (to include the execution log in the response)
    * @param payload the JSON provided to Lambda function as input
    * @param payloadObfuscated the JSON provided to Lambda function as input without refreshToken
    */
  @SuppressWarnings(Array("CatchException"))
  def invokeLambdaFunction(
      functionName: String,
      invocationType: String,
      logType: String,
      payload: String,
      payloadObfuscated: String
  ): Unit = {
    val request: InvokeRequest = new InvokeRequest()
      .withFunctionName(functionName)
      .withInvocationType(invocationType)
      .withLogType(logType)
      .withPayload(payload)

    logger.debug(s"Using ${lambdaConfig.environment} in AWS Lambda")

    if (runLambda) {
      logger.debug(
        s"Trying to invoke lambda function: $functionName with overview input: $payloadObfuscated"
      )
      try {
        val invokeResult: InvokeResult = lambdaClient.invoke(request);
        logger.debug(s"Invoke Lambda Function Result: $invokeResult")
      } catch {
        case e: Exception =>
          logger.error(
            s"There was an error invoking $functionName; Exception: ${e.getLocalizedMessage}"
          )
          throw e
      }
    } else {
      logger.debug(
        s"Not invoking AWS Lambda -- not in production or staging, in ${lambdaConfig.environment}")
      logger.debug(
        s"Lambda Function: $functionName -- Payload: $payloadObfuscated")
    }

  }

  def kickoffLayerOverviewCreate(
      projectId: UUID,
      layerId: UUID,
      invocationType: String = "Event"
  ): Unit = {
    val functionName: String =
      s"func${lambdaConfig.environment}GenerateProjectLayerOverviews"
    val logType: String = "Tail"
    val outputLocation: String =
      s"s3://${s3Config.dataBucket}/lambdaOverviews/projects/${projectId
        .toString()}/${layerId.toString()}-overview.tif"
    val refreshToken: String = auth0Config.systemRefreshToken
    val minZoomLevel: Int = 6
    val payloadcs: OverviewInput = OverviewInput(
      outputLocation,
      projectId,
      layerId,
      refreshToken,
      minZoomLevel
    )
    val payload: String = payloadcs.asJson.noSpaces
    val payloadObfuscated: String =
      payloadcs.copy(refreshToken = "").asJson.toString
    invokeLambdaFunction(
      functionName,
      invocationType,
      logType,
      payload,
      payloadObfuscated
    )
  }
}

object AWSLambda extends AWSLambda
