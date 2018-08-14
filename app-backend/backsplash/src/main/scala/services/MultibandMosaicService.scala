package com.azavea.rf.backsplash.services

import com.azavea.rf.backsplash.parameters.PathParameters._
import com.azavea.rf.backsplash.nodes.ProjectNode
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.database.SceneToProjectDao
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel.SceneType
import com.azavea.maml.eval.BufferingInterpreter

import cats._
import cats.data._
import cats.data.Validated._
import cats.effect.{IO, Timer}
import cats.implicits._
import cats.syntax._
import doobie.implicits._
import geotrellis.proj4.WebMercator
import geotrellis.raster.{IntArrayTile, MultibandTile, Raster, Tile}
import geotrellis.raster.render.ColorRamps
import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.maml._
import geotrellis.server.core.maml.reification.MamlTmsReification
import geotrellis.vector.{Projected, Polygon}
import io.circe._
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl._
import org.http4s.dsl.io._

import java.net.URI
import java.util.UUID

class MultibandMosaicService(
  interpreter: BufferingInterpreter = BufferingInterpreter.DEFAULT
)(implicit t: Timer[IO]) extends Http4sDsl[IO] with RollbarNotifier {

  implicit val xa = RFTransactor.xa

  // final val eval = MamlTms.curried(RasterVar("identity"), interpreter)
  final val eval = MamlTms.identity[ProjectNode](interpreter)

  val service: HttpService[IO] = {
    HttpService[IO] {
      case GET -> Root / ProjectNodeWrapper(projectNode) / IntVar(z) / IntVar(x) / IntVar(y) =>
        val paramMap = Map("identity" -> projectNode)
        val result = eval(paramMap, z, x, y).attempt
        result flatMap {
          case Right(Valid(tile)) =>
            println("doing great")
            Ok(tile.renderPng.bytes)
          case Right(Invalid(e)) =>
            println("invalid")
            BadRequest(e.toString)
          case Left(e) =>
            println("general error")
            BadRequest(e.getMessage)
        }
    }
  }
}
