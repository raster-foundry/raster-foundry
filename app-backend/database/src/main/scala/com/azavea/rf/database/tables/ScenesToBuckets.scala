package com.azavea.rf.database.tables

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._

/** Table description of table scenes_to_buckets. Objects of this class serve as prototypes for rows in queries. */
class ScenesToBuckets(_tableTag: Tag) extends Table[SceneToBucket](_tableTag, "scenes_to_buckets") {
  def * = (sceneId, bucketId) <> (SceneToBucket.tupled, SceneToBucket.unapply)
  /** Maps whole row to an option. Useful for outer joins. */
  def ? = (Rep.Some(sceneId), Rep.Some(bucketId)).shaped.<>({r=>import r._; _1.map(_=> SceneToBucket.tupled((_1.get, _2.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val sceneId: Rep[java.util.UUID] = column[java.util.UUID]("scene_id")
  val bucketId: Rep[java.util.UUID] = column[java.util.UUID]("bucket_id")

  val pk = primaryKey("scenes_to_buckets_pkey", (sceneId, bucketId))

  lazy val bucketsFk = foreignKey("scenes_to_buckets_bucket_id_fkey", bucketId, Buckets)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  lazy val scenesFk = foreignKey("scenes_to_buckets_scene_id_fkey", sceneId, Scenes)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
}

/** Collection-like TableQuery object for table ScenesToBuckets */
object ScenesToBuckets extends TableQuery(tag => new ScenesToBuckets(tag))
