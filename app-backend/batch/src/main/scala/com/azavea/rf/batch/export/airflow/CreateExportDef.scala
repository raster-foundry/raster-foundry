package com.azavea.rf.batch.export.airflow

import com.azavea.rf.batch._
import com.azavea.rf.batch.util._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.tables._
import com.azavea.rf.datamodel._

import org.xbill.DNS._
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder
import com.amazonaws.services.elasticmapreduce.model.{AddJobFlowStepsRequest, AddJobFlowStepsResult, HadoopJarStepConfig, StepConfig}
import io.circe.syntax._
import cats._
import cats.data._
import cats.implicits._

import java.util.UUID

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util._

case class CreateExportDef(exportId: UUID, region: Option[String] = None)(implicit val database: DB) extends Job {
  val name = CreateExportDef.name

  /** Get S3 client per each call */
  def s3Client = S3(region = region)

  protected def writeExportDefToS3(exportDef: ExportDefinition): Future[Option[String]] = {
    logger.info(s"Uploading export definition ${exportDef.id.toString} to S3 at ${exportDefConfig.bucketName}")
    val uri = s"rasterfoundry-development-data-us-east-1/${exportDefConfig.bucketName}"
    val future = Future[Option[String]] {
      s3Client.putObject(uri, s"${exportDef.id.toString}.json", exportDef.asJson.toString)
      Some(uri)
    }

    future onComplete {
      case Success(_) => {
        logger.info(s"Export definition uploaded to S3 at ${uri}")
      }
      case Failure(ex) => {
        logger.error(s"An error occurred uploading export defintion to S3 for ${exportDef.id}")
        logger.error(ex.stackTraceString)
      }
    }

    future
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

    val startEmr = (ed: ExportDefinition, edu: String) => Future { startExportEmrJob(ed, edu) }

    val createExportDef = for {
      user <- fromOptionF[Future, String, User](Users.getUserById(airflowUser), "DB: Failed to fetch User.")
      export <- fromOptionF[Future, String, Export](
        database.db.run(Exports.getExport(exportId, user)), "DB: Failed to fetch Export."
      )
      exportDef <- fromOptionF[Future, String, ExportDefinition](
        Exports.getExportDefinition(export, user), "DB: Failed to fetch ExportDefinition."
      )
      exportDefUri <- fromOptionF[Future, String, String](
        writeExportDefToS3(exportDef), s"Failed to write ExportDefinition to S3:\n${exportDef.asJson.spaces2}"
      )
      exportStatus <- EitherT.right[Future, String, Int](database.db.run(Exports.updateExport(
        updateExportStatus(export, ExportStatus.Exporting),
        exportId,
        user
      )))
      emrJobStatus <- EitherT.right[Future, String, Unit](
        startEmr(exportDef, exportDefUri).map({ result =>
          result.getStepIds.asScala.headOption.foreach(stepId => println(s"StepId: $stepId"))
          export
        })
          .recover({
            case e: Throwable => {
              logger.error(s"An error occurred during export ${export.id}. Skipping...")
              logger.error(e.stackTraceString)
              sendError(e)
              updateExportStatus(export, ExportStatus.Failed)
            }
          })
          .flatMap({ result =>
            database.db.run(Exports.updateExport(result, exportId, user)).map(_ => ())
          })
      )
    } yield emrJobStatus

    createExportDef.value.onComplete {
      case Success(Left(e)) => {
        logger.error(e)
        sendError(e)
        stop
        sys.exit(1)
      }
      case Success(_) => {
        logger.info("Export job sent to cluster and status updated")
        stop
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
    implicit val db = DB.DEFAULT

    val job = args.toList match {
      case List(exportId, region) => CreateExportDef(UUID.fromString(exportId), Some(region))
      case List(exportId) => CreateExportDef(UUID.fromString(exportId))
      case _ =>
        throw new IllegalArgumentException("Argument could not be parsed to UUID")
    }

    job.run
  }
}
