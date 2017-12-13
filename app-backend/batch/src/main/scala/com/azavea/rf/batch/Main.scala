package com.azavea.rf.batch

import com.azavea.rf.batch.aoi.FindAOIProjects
import com.azavea.rf.batch.export.spark.Export
import com.azavea.rf.batch.export.{CheckExportStatus, CreateExportDef, DropboxCopy, S3Copy}
import com.azavea.rf.batch.healthcheck.HealthCheck
import com.azavea.rf.batch.ingest.spark.Ingest
import com.azavea.rf.batch.aoi.UpdateAOIProject
import com.azavea.rf.batch.landsat8.{ImportLandsat8, ImportLandsat8C1}
import com.azavea.rf.batch.migration.S3ToPostgres
import com.azavea.rf.batch.sentinel2.ImportSentinel2
import com.azavea.rf.batch.stac.{ReadStacFeature}
import com.azavea.rf.batch.notification.NotifyIngestStatus

object Main {
  val modules = Map[String, Array[String] => Unit](
    Export.name             -> (Export.main(_)),
    Ingest.name             -> (Ingest.main(_)),
    ImportLandsat8.name     -> (ImportLandsat8.main(_)),
    ImportSentinel2.name    -> (ImportSentinel2.main(_)),
    CreateExportDef.name    -> (CreateExportDef.main(_)),
    FindAOIProjects.name    -> (FindAOIProjects.main(_)),
    UpdateAOIProject.name   -> (UpdateAOIProject.main(_)),
    S3Copy.name             -> (S3Copy.main(_)),
    DropboxCopy.name        -> (DropboxCopy.main(_)),
    ImportLandsat8C1.name   -> (ImportLandsat8C1.main(_)),
    CheckExportStatus.name  -> (CheckExportStatus.main(_)),
    S3ToPostgres.name       -> (S3ToPostgres.main(_)),
    HealthCheck.name        -> (HealthCheck.main(_)),
    ReadStacFeature.name    -> (ReadStacFeature.main(_)),
    NotifyIngestStatus.name -> (NotifyIngestStatus.main(_))
  )

  def main(args: Array[String]): Unit = {
    args.headOption match {
      case Some(head) => {
        modules.get(head) match {
          case Some(main) => main(args.tail)
          case _ => throw new Exception(s"No job ${head} available (all available jobs: ${modules.keys.mkString(", ")})")
        }
      }
      case _ => throw new Exception(s"No options passed: ${args.toList}")
    }
  }
}
