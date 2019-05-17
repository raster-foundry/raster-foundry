package com.rasterfoundry.datamodel

import java.time.Instant
import java.util.UUID

final case class TaskProperties(
  id: UUID,
  createdAt: Instant,
  createdBy: String,
  modifiedAt: Instant,
  modifiedBy: String,
  projectId: UUID,
  projectLayerId: UUID,
  status: TaskStatus,
  lockedBy: String,
  lockedOn: Instant,
  actions: List[TaskActionStamp]
)
