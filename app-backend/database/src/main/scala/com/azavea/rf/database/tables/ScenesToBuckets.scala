package com.azavea.rf.database.tables

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import java.util.UUID

/** Table description of table scenes_to_buckets. Objects of this class serve as prototypes for rows in queries. */
class ScenesToBuckets(_tableTag: Tag) extends Table[SceneToBucket](_tableTag, "scenes_to_buckets") {
  def * = (sceneId, bucketId, sceneOrder) <> (SceneToBucket.tupled, SceneToBucket.unapply)

  val sceneId: Rep[UUID] = column[UUID]("scene_id")
  val bucketId: Rep[UUID] = column[UUID]("bucket_id")
  val sceneOrder: Rep[Option[Int]] = column[Option[Int]]("scene_order")

  val pk = primaryKey("scenes_to_buckets_pkey", (sceneId, bucketId))

  lazy val bucketsFk = foreignKey("scenes_to_buckets_bucket_id_fkey", bucketId, Buckets)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val scenesFk = foreignKey("scenes_to_buckets_scene_id_fkey", sceneId, Scenes)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
}

/** Collection-like TableQuery object for table ScenesToBuckets */
object ScenesToBuckets extends TableQuery(tag => new ScenesToBuckets(tag)) with LazyLogging {
  val scenesToBuckets = TableQuery[ScenesToBuckets]

  def resetSceneOrderAction(bucketId: UUID) = {
    val query = for {
      s2b <- scenesToBuckets if s2b.bucketId === bucketId
    } yield s2b.sceneOrder

    query.update(None)
  }

  def addSceneOrderAction(bucketId: UUID, orderedScenes: Seq[UUID]) =
    DBIO.sequence(orderedScenes.zipWithIndex.map { case (sceneId, order) =>
      val q = for {
        s2b <- scenesToBuckets if s2b.bucketId === bucketId && s2b.sceneId === sceneId
      } yield s2b.sceneOrder

      q.update(Some(order))
    })

  def setSceneOrder(bucketId: UUID, orderedScenes: Seq[UUID])(implicit database: DB) = database.db.run {
    resetSceneOrderAction(bucketId) andThen addSceneOrderAction(bucketId, orderedScenes)
  }

}
