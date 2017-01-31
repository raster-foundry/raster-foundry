package com.azavea.rf.tile

import com.azavea.rf.datamodel.Scene
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.azavea.rf.ingest.util.S3.S3UrlRx
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Scenes

import java.util.UUID

import geotrellis.spark.LayerId

/**
  * RasterFoundry layers belong to user and organization.
  * Eventually these must be mapped to an S3 prefix.
  * This class encapsulates the relationship and gives a place to modify the mapping
  */
case class RfLayerId(sceneId: UUID) {
  def prefix(implicit database: Database): Future[Option[String]] = {
    val sceneQuery = Scenes.getScene(sceneId)
    sceneQuery.map { sceneOption =>
      for {
        scene <- sceneOption
        ingestLocation <- scene.ingestLocation
        result <- S3UrlRx.findFirstMatchIn(ingestLocation)
      } yield result.group("prefix")
    }
  }
  def catalogId(zoom: Int) = LayerId(sceneId.toString, zoom)
  def layerName: String = sceneId.toString
}
