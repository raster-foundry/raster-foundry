package com.azavea.rf.tile.routes

import com.azavea.rf.tile._
import com.azavea.rf.tile.image._
import com.azavea.rf.database.Database

import geotrellis.raster._
import geotrellis.raster.render.Png
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import com.typesafe.scalalogging.LazyLogging
import cats.implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID

object MosaicRoutes extends LazyLogging {

  val emptyTilePng = IntArrayTile.ofDim(256, 256).renderPng

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))


  def mosaicProject(projectId: UUID)(implicit database: Database): Route =
    pathPrefix("export") {
      parameter("bbox".?, "zoom".as[Int]?) { (bbox, zoom) =>
        get {
          complete {
            Mosaic.render(projectId, zoom, bbox)
              .map(_.renderPng)
              .map(pngAsHttpResponse)
              .value
          }
        }
      }
    } ~ pathPrefix (IntNumber / IntNumber / IntNumber ) { (zoom, x, y) =>
      parameter("tag".?) { tag =>
        get {
          complete {
            Mosaic(projectId, zoom, x, y, tag)
              .map(_.renderPng)
              .getOrElse(emptyTilePng)
              .map(pngAsHttpResponse)
          }
        }
      }
    }

// TODO: re-enable this, needed for project color correction endpoint
//   def mosaicScenes: Route =
//     pathPrefix(JavaUUID / Segment / "mosaic" / IntNumber / IntNumber / IntNumber) { (orgId, userId, zoom, x, y) =>
//       colorCorrectParams { params =>
//         parameters('scene.*) { scenes =>
//           get {
//             complete {
//               val ids = scenes.map(id => RfLayerId(orgId, userId, UUID.fromString(id)))
//               Mosaic(params, ids, zoom, x, y).map { maybeTile =>
//                 maybeTile.map { tile => pngAsHttpResponse(tile.renderPng())}
//               }
//             }
//           }
//         }
//       }
//     }
}
