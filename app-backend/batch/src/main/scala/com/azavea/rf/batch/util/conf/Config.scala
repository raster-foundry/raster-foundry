package com.azavea.rf.batch.util.conf

import com.azavea.rf.datamodel.Band

import geotrellis.proj4.CRS
import shapeless.syntax.typeable._
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.readers.ArbitraryTypeReader
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.{DbxAppInfo, DbxRequestConfig}

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
    usgsLandsatUrlC1: String,
    awsRegion: Option[String],
    awsLandsatBase: String,
    awsLandsatBaseC1: String,
    bucketName: String
  ) {
    def organizationUUID = UUID.fromString(organization)
    def datasourceUUID = UUID.fromString(datasourceId)
  }

  protected case class ExportDef(
    awsRegion: Option[String],
    bucketName: String,
    awsDataproc: String,
    sparkJarS3: String,
    sparkJar: String,
    sparkClass: String,
    sparkMemory: String
  )

  protected case class Sentinel2Bands(
    // 10m
    B02: Band.Create,
    B03: Band.Create,
    B04: Band.Create,
    B08: Band.Create,
    // 20m
    B05: Band.Create,
    B06: Band.Create,
    B07: Band.Create,
    B8A: Band.Create,
    B11: Band.Create,
    B12: Band.Create,
    // 60m
    B01: Band.Create,
    B09: Band.Create,
    B10: Band.Create
  )

  protected case class Sentinel2(
    organization: String,
    bandLookup: Sentinel2Bands,
    datasourceId: String,
    baseHttpPath: String,
    awsRegion: Option[String],
    bucketName: String,
    targetProj: String
  ) {
    def organizationUUID = UUID.fromString(organization)
    def datasourceUUID = UUID.fromString(datasourceId)
    def targetProjCRS = CRS.fromName(targetProj)
    def bandByName(key: String): Option[Band.Create] =
      bandLookup.getClass.getDeclaredFields.toList.filter(_.getName == key).map { field =>
        field.setAccessible(true)
        field.get(bandLookup).cast[Band.Create] // safe shapeless cast, instead asInstanceOf call
      }.headOption.flatten
  }

  case class Dropbox(appKey: String, appSecret: String) {
    lazy val appInfo = new DbxAppInfo(appKey, appSecret)
    lazy val config  = new DbxRequestConfig("azavea/rf-dropbox-test")

    def client(accessToken: String) = new DbxClientV2(config, accessToken)
  }

  private lazy val config = ConfigFactory.load()
  protected lazy val landsat8Config = config.as[Landsat8]("landsat8")
  protected lazy val sentinel2Config = config.as[Sentinel2]("sentinel2")
  protected lazy val airflowUser = config.as[String]("airflow.user")
  protected lazy val exportDefConfig = config.as[ExportDef]("export-def")
  protected lazy val dropboxConfig = config.as[Dropbox]("dropbox")
  val jarPath = "s3://us-east-1.elasticmapreduce/libs/script-runner/script-runner.jar"
}
