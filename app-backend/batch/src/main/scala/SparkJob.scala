package com.rasterfoundry.batch

import com.rasterfoundry.common.RollbarNotifier

import cats.effect.{IO, Resource, IOApp, ExitCode}

import org.apache.spark._

trait SparkJob extends IOApp with Job {
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

  private def acquireSparkContext =
    IO { logger.info("Acquiring spark context") } map { _ =>
      new SparkContext(conf)
    }
  private def releaseSparkContext(sc: SparkContext) =
    IO { logger.info("Releasing spark context") } map { _ =>
      sc.stop()
    } map { _ =>
      logger.info("Spark context released")
    }
  val scResource: Resource[IO, SparkContext] =
    Resource.make(acquireSparkContext)(releaseSparkContext)

}
