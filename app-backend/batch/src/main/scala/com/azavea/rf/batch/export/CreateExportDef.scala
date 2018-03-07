package com.azavea.rf.batch.export

import java.util.UUID

import cats.data._
import cats.effect.IO
import cats.implicits._
import com.amazonaws.{AmazonServiceException, SdkClientException}
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder
import com.amazonaws.services.elasticmapreduce.model.{AddJobFlowStepsRequest, AddJobFlowStepsResult, HadoopJarStepConfig, StepConfig}
import com.azavea.rf.batch._
import com.azavea.rf.batch.util._
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.database.{ExportDao, UserDao}
import com.azavea.rf.datamodel._
import doobie.util.transactor.Transactor
import io.circe.syntax._
import org.xbill.DNS._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util._

case class CreateExportDef(exportId: UUID, region: Option[String] = None)(implicit val xa: Transactor[IO]) extends Job {
  val name = CreateExportDef.name

  /** Get S3 client per each call */
  def s3Client = S3(region = region)

  protected def writeExportDefToS3(exportDef: ExportDefinition): Unit = {
    logger.info(s"Uploading export definition ${exportDef.id.toString} to S3 at ${exportDefConfig.bucketName}")

    val uri = getExportURI(exportDef)
    try {
      s3Client.putObject(uri, s"${exportDef.id.toString}.json", exportDef.asJson.toString)
    } catch {
      case e: Throwable => {
        logger.error(s"Failed to put export defintion ${exportDef.id} => ${uri}")
        throw e
      }
    }
  }

  def getExportURI(exportDefinition: ExportDefinition): String = {
    s"rasterfoundry-development-data-us-east-1/${exportDefConfig.bucketName}"
  }

  protected def getClusterId() = {
    val dnsLookup = new Lookup(exportDefConfig.awsDataproc, Type.TXT, DClass.IN)
    dnsLookup.run()
    val clusterId = dnsLookup.getAnswers().map(_.rdataToString).head.replace("\"", "")
    clusterId
  }

  protected def startExportEmrJob(exportDef: ExportDefinition, exportDefUri: String): AddJobFlowStepsResult = {
    val jarStep = new HadoopJarStepConfig()
    jarStep.setJar(jarPath)
    jarStep.setArgs(
      List(
        "/usr/bin/spark-submit", "--master", "yarn", "--deploy-mode",
        "cluster", "--conf", "spark.yarn.submit.waitAppCompletion=false",
        "--class", exportDefConfig.sparkClass, "--driver-memory", exportDefConfig.sparkMemory,
        s"${exportDefConfig.sparkJarS3}/${exportDefConfig.sparkJar}",
        "-j", s"s3://${exportDefUri}/${exportDef.id}.json"
      ).asJava
    )

    val steps = new StepConfig()
    steps.setName(s"export-${exportDef.id}")
    steps.setActionOnFailure("CONTINUE")
    steps.setHadoopJarStep(jarStep)

    val jobSteps = new AddJobFlowStepsRequest()
    jobSteps.setJobFlowId(getClusterId())
    jobSteps.setSteps(List(steps).asJava)

    val emrClient = AmazonElasticMapReduceClientBuilder.standard().withCredentials(s3Client.credentialsProviderChain).build()
    emrClient.addJobFlowSteps(jobSteps)
  }

  def updateExportStatus(export: Export, status: ExportStatus): Export =
    export.copy(exportStatus = status)

  def run: Unit = {
    logger.info("Starting export process...")

    val processingExport = for {
      user <- UserDao.query.filter(fr"id = ${systemUser}").select
      export <- ExportDao.query.filter(fr"id = ${exportId}").select
      exportDef <- ExportDao.getExportDefinition(export, user)
      _ <- {
        writeExportDefToS3(exportDef)
        ExportDao.update(updateExportStatus(export, ExportStatus.Exporting), exportId, user)
      }
    } yield {
      val uri = getExportURI(exportDef)
      try {
        startExportEmrJob(exportDef, uri)
        val exportStarted = updateExportStatus(export, ExportStatus.Exporting)
        ExportDao.update(exportStarted, exportId, user)
        exportStarted
      } catch {
        case e: Throwable => {
          logger.error(s"An error occurred during export ${export.id}. Skipping...")
          logger.error(e.stackTraceString)
          sendError(e)
          val exportFailed = updateExportStatus(export, ExportStatus.Failed)
          ExportDao.update(exportFailed, exportId, user)
          exportFailed
        }
      }
    }

    processingExport.transact(xa).unsafeToFuture onComplete {
      case Success(export) => {
        if (export.exportStatus == ExportStatus.Failed) {
          logger.error("Export job failed to send to cluster; status not updated")
          sendError("Export job failed to send to cluster; status not updated")
          stop
          sys.exit(1)
        } else {
          logger.info("Export job sent to cluster and status updated")
          stop
        }
      }
      case Failure(e) => {
        logger.error(e.stackTraceString)
        sendError(e)
        stop
        sys.exit(1)
      }
    }
  }
}

object CreateExportDef {
  val name = "create_export_def"

  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa

    val job = args.toList match {
      case List(exportId, region) => CreateExportDef(UUID.fromString(exportId), Some(region))
      case List(exportId) => CreateExportDef(UUID.fromString(exportId))
      case _ =>
        throw new IllegalArgumentException("Argument could not be parsed to UUID")
    }

    job.run
  }
}
