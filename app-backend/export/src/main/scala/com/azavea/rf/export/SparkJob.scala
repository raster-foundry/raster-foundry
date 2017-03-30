package com.azavea.rf.export

import org.apache.spark._

trait SparkJob {
  // Functions for combine step
  def createTiles[V](value: V): Seq[V]                         = Seq(value)
  def mergeTiles1[V](values: Seq[V], value: V): Seq[V]         = values :+ value
  def mergeTiles2[V](values1: Seq[V], values2: Seq[V]): Seq[V] = values1 ++ values2

  // Some of these options can be set by way of the spark-submit command
  val conf: SparkConf =
      new SparkConf()
        .setAppName("Raster Foundry Export")
        .set("spark.serializer", classOf[org.apache.spark.serializer.KryoSerializer].getName)
        .set("spark.kryo.registrator", classOf[geotrellis.spark.io.kryo.KryoRegistrator].getName)
        .setIfMissing("spark.master", "local[*]")
}
