package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

/** The object which models data stored on the many-to-many scene <-> bucket table */
case class SceneToBucket(
  sceneId: UUID,
  bucketId: UUID,
  sceneOrder: Option[Int] = None
)

