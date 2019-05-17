package com.rasterfoundry.datamodel

case class TaskActionStamp(
  userId: String,
  timestamp: Instant,
  fromStatus: TaskStatus,
  toStatus: TaskStatus
)
