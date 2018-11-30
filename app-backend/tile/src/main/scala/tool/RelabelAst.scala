package com.rasterfoundry.tile.tool

import com.rasterfoundry.tile._
import com.rasterfoundry.tool.ast._
import com.rasterfoundry.tool.maml._

import doobie._
import doobie.implicits._
import cats._
import cats.effect._
import cats.implicits._

import scala.concurrent.{Future, ExecutionContext}
import java.util.UUID

object RelabelAst {

  def cogScenes(ast: MapAlgebraAST)(
      implicit xa: Transactor[IO],
      ec: ExecutionContext): Future[Option[MapAlgebraAST]] = {
    val sources: List[Future[Option[(UUID, MapAlgebraAST)]]] =
      ast.tileSources.toList.map {
        case MapAlgebraAST.SceneRaster(id, sceneId, band, celltype, metadata) =>
          import com.rasterfoundry.database._
          import com.rasterfoundry.database.Implicits._
          import com.rasterfoundry.datamodel._
          import cats.implicits._

          SceneDao.query
            .filter(sceneId)
            .selectOption
            .transact(xa)
            .unsafeToFuture
            .map { maybeScene: Option[Scene] =>
              maybeScene match {
                case Some(scene) =>
                  scene.sceneType match {
                    case Some(SceneType.COG) =>
                      scene.ingestLocation.map { location =>
                        id -> MapAlgebraAST.CogRaster(id,
                                                      sceneId,
                                                      band,
                                                      celltype,
                                                      metadata,
                                                      location)
                      }
                    case _ => None
                  }
                case _ => None
              }
            }
        case _ => Future.successful(None)
      }

    sources.sequence.map { maybeSources =>
      val subsitutions = maybeSources.flatten.toMap
      ast.substitute(subsitutions)
    }
  }

}
