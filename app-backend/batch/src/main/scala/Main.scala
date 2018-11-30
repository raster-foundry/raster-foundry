package com.rasterfoundry.batch

import com.rasterfoundry.batch.aoi.FindAOIProjects
import com.rasterfoundry.batch.cogMetadata.HistogramBackfill
import com.rasterfoundry.batch.export.spark.Export
import com.rasterfoundry.batch.export.{CreateExportDef, UpdateExportStatus}
import com.rasterfoundry.batch.healthcheck.HealthCheck
import com.rasterfoundry.batch.aoi.UpdateAOIProject
import com.rasterfoundry.batch.landsat8.{ImportLandsat8, ImportLandsat8C1}
import com.rasterfoundry.batch.sentinel2.ImportSentinel2
import com.rasterfoundry.batch.stac.{ReadStacFeature}
import com.rasterfoundry.batch.notification.NotifyIngestStatus

object Main {
  val modules = Map[String, Array[String] => Unit](
    CreateExportDef.name -> (CreateExportDef.main(_)),
    UpdateExportStatus.name -> (UpdateExportStatus.main(_)),
    Export.name -> (Export.main(_)),
    FindAOIProjects.name -> (FindAOIProjects.main(_)),
    HealthCheck.name -> (HealthCheck.main(_)),
    HistogramBackfill.name -> (HistogramBackfill.main(_)),
    ImportLandsat8.name -> (ImportLandsat8.main(_)),
    ImportLandsat8C1.name -> (ImportLandsat8C1.main(_)),
    ImportSentinel2.name -> (ImportSentinel2.main(_)),
    NotifyIngestStatus.name -> (NotifyIngestStatus.main(_)),
    ReadStacFeature.name -> (ReadStacFeature.main(_)),
    UpdateAOIProject.name -> (UpdateAOIProject.main(_))
  )

  def main(args: Array[String]): Unit = {
    args.headOption match {
      case Some(head) => {
        modules.get(head) match {
          case Some(main) => main(args.tail)
          case _ =>
            throw new Exception(
              s"No job ${head} available (all available jobs: ${modules.keys.mkString(", ")})")
        }
      }
      case _ => throw new Exception(s"No options passed: ${args.toList}")
    }
    System.exit(0)
  }
}
