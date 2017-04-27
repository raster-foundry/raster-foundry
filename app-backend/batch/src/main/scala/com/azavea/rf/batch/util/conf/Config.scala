package com.azavea.rf.batch.util.conf

import com.azavea.rf.datamodel.Band

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.readers.ArbitraryTypeReader

import java.util.UUID

trait Config {
  import Ficus._
  import ArbitraryTypeReader._

  protected case class Landsat8Bands(
    `15m`: List[Band.Create],
    `30m`: List[Band.Create]
  )

  protected case class Landsat8(
    organization: String,
    bandLookup: Landsat8Bands,
    datasourceId: String,
    usgsLandsatUrl: String,
    awsRegion: Option[String],
    awsLandsatBase: String,
    bucketName: String
  ) {
    def organizationUUID = UUID.fromString(organization)
    def datasourceUUID = UUID.fromString(datasourceId)
  }

  private lazy val config = ConfigFactory.load()
  protected lazy val landsat8Config = config.as[Landsat8]("landsat8")
  protected lazy val airflowUser = config.as[String]("airflow.user")
}
