package com.azavea.rf.batch

import org.apache.spark._
trait SparkJob {
  // Functions for combine step
  def createTiles[V](value: V): Seq[V] = Seq(value)
  def mergeTiles1[V](values: Seq[V], value: V): Seq[V] = values :+ value
  def mergeTiles2[V](values1: Seq[V], values2: Seq[V]): Seq[V] =
    values1 ++ values2

  val jobName: String
  lazy val name = jobName.toLowerCase

  // Some of these options can be set by way of the spark-submit command
  def conf: SparkConf =
    new SparkConf()
      .setAppName(s"Raster Foundry $jobName")
      .set("spark.serializer",
           classOf[org.apache.spark.serializer.KryoSerializer].getName)
      .set("spark.kryo.registrator",
           classOf[geotrellis.spark.io.kryo.KryoRegistrator].getName)
      .set("spark.kryoserializer.buffer.max", "512m")
      .setIfMissing("spark.master", "local[*]")
}
