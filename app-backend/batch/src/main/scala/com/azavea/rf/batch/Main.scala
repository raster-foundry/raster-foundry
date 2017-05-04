package com.azavea.rf.batch

import com.azavea.rf.batch.export.spark.Export
import com.azavea.rf.batch.export.airflow.CreateExportDef
import com.azavea.rf.batch.ingest.spark.Ingest
import com.azavea.rf.batch.landsat8.airflow.ImportLandsat8
import com.azavea.rf.batch.sentinel2.airflow.ImportSentinel2

object Main {
  val modules = Map[String, Array[String] => Unit](
    Export.name          -> (Export.main(_)),
    Ingest.name          -> (Ingest.main(_)),
    ImportLandsat8.name  -> (ImportLandsat8.main(_)),
    ImportSentinel2.name -> (ImportSentinel2.main(_)),
    CreateExportDef.name -> (CreateExportDef.main(_))
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
