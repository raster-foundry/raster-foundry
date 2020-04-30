package com.rasterfoundry.database

import doobie._
import doobie.postgres.implicits._

import java.util.UUID

/** Container for methods required to get byte arrays of MVT layers from the db
  *
  * Claims to be a Dao, but doesn't extend Dao[Option[Array[Byte]]] because we can't provide
  * sensible values for some of the required Dao fields, e.g., what should the `selectF` be?
  * Or fieldnames? Or table name?
  */
object MVTLayerDao {
  def getAnnotationProjectTasks(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): ConnectionIO[Option[Array[Byte]]] = ???

  def getAnnotationProjectLabels(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): ConnectionIO[Option[Array[Byte]]] = ???
}
