package com.azavea.rf.tile.tool

import com.azavea.rf.tile._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.maml._

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
          import com.azavea.rf.database._
          import com.azavea.rf.database.Implicits._
          import com.azavea.rf.datamodel._
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
