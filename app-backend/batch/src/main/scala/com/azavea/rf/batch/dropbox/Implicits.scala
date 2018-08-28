package com.azavea.rf.batch.dropbox

import java.io.{
  ByteArrayInputStream,
  ByteArrayOutputStream,
  DataOutputStream,
  InputStream
}

import geotrellis.raster.CellGrid
import geotrellis.raster.io.geotiff.GeoTiff
import geotrellis.raster.io.geotiff.writer.GeoTiffWriter
import geotrellis.util.MethodExtensions
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.spark.SparkContext

trait Implicits {

  trait HadoopRasterMethods[T] extends MethodExtensions[T] {
    def write(path: Path)(implicit sc: SparkContext): Unit =
      write(path, sc.hadoopConfiguration)

    def write(path: Path, conf: Configuration): Unit
  }

  @SuppressWarnings(Array("ClassNames"))
  implicit class withGeoTiffWriteMethods[T <: CellGrid](val self: GeoTiff[T]) {
    def dropboxWrite(save: InputStream => String): String = {
      val bos = new ByteArrayOutputStream()
      try {
        val dos = new DataOutputStream(bos)
        try {
          new GeoTiffWriter(self, dos).write()
          save(new ByteArrayInputStream(bos.toByteArray))
        } finally {
          dos.close
        }
      } finally {
        bos.close
      }
    }
  }

}
