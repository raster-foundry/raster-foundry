package com.azavea.rf.tile.tool

import com.azavea.rf.tile.image.Mosaic
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.maml._
import com.azavea.rf.datamodel._
import com.azavea.maml.ast._
import com.azavea.maml.eval._
import com.azavea.maml.eval.tile._
import com.azavea.maml.util.NeighborhoodConversion
import com.azavea.rf.database.util.RFTransactor
import org.scalatest._
import cats._
import cats.data.Validated._
import cats.data.{NonEmptyList => NEL, _}
import cats.implicits._
import cats.effect.IO
import io.circe._
import io.circe.syntax._
import com.typesafe.scalalogging.LazyLogging
import doobie.util.transactor.Transactor
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.tiling._
import geotrellis.vector.{Extent, MultiPolygon, Projected}
import geotrellis.spark.io.postgres.PostgresAttributeStore

import scala.util.{Failure, Success, Try}
import scala.concurrent._
import scala.concurrent.duration._
import java.util.UUID
import java.net.URI
import java.sql.Timestamp
import java.time.Instant

import com.azavea.rf.common.utils.CogUtils

import scala.concurrent.{ExecutionContext, Future}


/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
class TileResolverSpec extends WordSpec with Matchers {
  implicit val xa = RFTransactor.xa
  val resolver = new TileResolver(implicitly[Transactor[IO]], ExecutionContext.global)

  "The tile resolver" when {
    "resolving literal tiles" should {
      "handle the COG case specially" in {

        val cogUri = "http://radiant-nasa-iserv.s3-us-west-2.amazonaws.com/2014/11/12/IPR201411121933360530S07841W/IPR201411121933360530S07841W-COG.tif"
        val ast = CogRaster(
          UUID.randomUUID(),
          Some(0),
          None,
          cogUri
        )

        val cogExtent = CogUtils.getTiffExtent(cogUri)
        val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
        val landsatId = UUID.fromString("697a0b91-b7a8-446e-842c-97cda155554d")

        val cogScene = Scene.
          Create(
          None, Visibility.Public, List("Test", "Public", "Low Resolution"), landsatId,
          Map("instrument type" -> "satellite", "splines reticulated" -> "0").asJson,
          "test scene datasource 1", None,
            cogExtent, cogExtent, List.empty[String], List.empty[Image.Banded], List.empty[Thumbnail.Identified],
          Some(cogUri),
          SceneFilterFields(None,
                            Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
                            None,
                            None),
          SceneStatusFields(JobStatus.Processing, JobStatus.Processing, IngestStatus.NotIngested),
          Some(SceneType.COG)
        )

        val response = Await.result(resolver.resolveBuffered(ast)(12, 1155, 2108), 10.seconds)
        response.isValid shouldBe true
      }
    }
  }

}
