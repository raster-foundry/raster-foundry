package com.rasterfoundry.batch

import com.rasterfoundry.batch.aoi.FindAOIProjects
import com.rasterfoundry.batch.cogMetadata.{HistogramBackfill, OverviewBackfill}
import com.rasterfoundry.batch.export.{CreateExportDef, UpdateExportStatus}
import com.rasterfoundry.batch.healthcheck.HealthCheck
import com.rasterfoundry.batch.aoi.UpdateAOIProject
import com.rasterfoundry.batch.stac.{ReadStacFeature}
import com.rasterfoundry.batch.notification.NotifyIngestStatus

object Main {
  val modules = Map[String, Array[String] => Unit](
    CreateExportDef.name -> (CreateExportDef.main(_)),
    FindAOIProjects.name -> (FindAOIProjects.main(_)),
    HealthCheck.name -> (HealthCheck.main(_)),
    HistogramBackfill.name -> (HistogramBackfill.main(_)),
    NotifyIngestStatus.name -> (NotifyIngestStatus.main(_)),
    OverviewBackfill.name -> (OverviewBackfill.main(_)),
    ReadStacFeature.name -> (ReadStacFeature.main(_)),
    UpdateAOIProject.name -> (UpdateAOIProject.main(_)),
    UpdateExportStatus.name -> (UpdateExportStatus.main(_))
  )

  def main(args: Array[String]): Unit = {
    println(args)
    println("lol")
    println("omg")
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
