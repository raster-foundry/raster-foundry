package com.azavea.rf.tile.routes

import com.azavea.rf.tile._
import com.azavea.rf.tile.image._
import com.azavea.rf.datamodel.ColorCorrect.Params.colorCorrectParams
import com.azavea.rf.database.Database

import geotrellis.raster.render.Png
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID


object MosaicRoutes extends LazyLogging {

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def mosaicProject(implicit db: Database): Route =
    pathPrefix(JavaUUID) { projectId =>
      pathPrefix("export") {
        parameter("bbox".?, "zoom".as[Int]?) { (bbox, zoom) =>
          get {
            complete {
              Mosaic.render(projectId, zoom, bbox).map { maybeRender =>
                maybeRender.map { tile => pngAsHttpResponse(tile.renderPng()) }
              }
            }
          }
        }
      } ~ pathPrefix (IntNumber / IntNumber / IntNumber ) { (zoom, x, y) =>
        parameter("tag".?) { tag =>
          get {
            complete {
              Mosaic(projectId, zoom, x, y, tag).map { maybeTile =>
                maybeTile.map { tile => pngAsHttpResponse(tile.renderPng()) }
              }
            }
          }
        }
      }
    }

// TODO: re-eable this, needed for project color correction endpoint
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
