package com.rasterfoundry.batch

import com.rasterfoundry.batch.cogMetadata._
import com.rasterfoundry.batch.export.{CreateExportDef, UpdateExportStatus}
import com.rasterfoundry.batch.geojsonImport.ImportGeojsonFiles
import com.rasterfoundry.batch.groundwork.{
  CreateTaskGrid,
  NotifyIntercomProgram
}
import com.rasterfoundry.batch.healthcheck.HealthCheck
import com.rasterfoundry.batch.notification.NotifyIngestStatus
import com.rasterfoundry.batch.projectLiberation.ProjectLiberation
import com.rasterfoundry.batch.removescenes.RemoveScenes
import com.rasterfoundry.batch.stacExport.WriteStacCatalog

object Main {
  val modules = Map[String, Array[String] => Unit](
    CreateExportDef.name -> (CreateExportDef.main(_)),
    HealthCheck.name -> (HealthCheck.main(_)),
    HistogramBackfill.name -> (HistogramBackfill.main(_)),
    RasterSourceMetadataBackfill.name -> (RasterSourceMetadataBackfill.main(_)),
    NotifyIngestStatus.name -> (NotifyIngestStatus.main(_)),
    WriteStacCatalog.name -> (WriteStacCatalog.main(_)),
    UpdateExportStatus.name -> (UpdateExportStatus.main(_)),
    ImportGeojsonFiles.name -> (ImportGeojsonFiles.main(_)),
    ProjectLiberation.name -> (ProjectLiberation.main(_)),
    CreateTaskGrid.name -> (CreateTaskGrid.main(_)),
    NotifyIntercomProgram.name -> (NotifyIntercomProgram.main(_)),
    RemoveScenes.name -> (RemoveScenes.main(_))
  )

  def main(args: Array[String]): Unit = {
    args.headOption match {
      case Some(head) => {
        modules.get(head) match {
          case Some(main) => main(args.tail)
          case _ =>
            throw new Exception(
              s"No job ${head} available (all available jobs: ${modules.keys.mkString(", ")})"
            )
        }
      }
      case _ => throw new Exception(s"No options passed: ${args.toList}")
    }
    System.exit(0)
  }
}
