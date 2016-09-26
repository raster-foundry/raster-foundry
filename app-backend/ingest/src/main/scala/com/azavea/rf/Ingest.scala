package com.azavea.rf

import spray.json._
import org.apache.spark._
import org.apache.spark.rdd._

object Ingest {
  def main(args: Array[String]): Unit = {

    // Some of these options can be set by way of the spark-submit command
    val conf: SparkConf =
      new SparkConf()
        .setAppName(s"Raster Foundry Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")

    // instantiate our spark context (implicit as a convention for functions which require an SC)
    implicit val sc = new SparkContext(conf)

    // here's a valid jobconfig
    val json = Seq(
      """{"scenes":[{"output":"s3://raster-foundary/userName/layerName","source":{"extent":[0,0,1,10],"urls":["s3://bucket/landsat/LC8_file1.tif","s3://bucket/landsat/LC8_file2.tif","s3://bucket/landsat/LC8_file3.tif"]}}]}""".parseJson,
      """{"scenes":[{"output":"s3://raster-foundary/userName/layerName","source":{"extent":[0,10,10,10],"urls":["s3://bucket/landsat/LC8_file1.tif","s3://bucket/landsat/LC8_file2.tif","s3://bucket/landsat/LC8_file3.tif"]}}]}""".parseJson
    )

    // parallelize this sequence
    val jobConfigs = sc.parallelize(json)

    // map over it (note that the mapPartitions method is often a more performant option)
    val parsedJobConfigs = jobConfigs.map({ jc =>
      jc.convertTo[JobDefinition]
    })

    // grab the first parsed bit of json and print it out to verify that things are working fine
    println(s"Here's a parsed job configuration returned from Spark: ${parsedJobConfigs.first}")
  }
}
