package com.azavea.rf.ingest

import com.azavea.rf.datamodel.IngestDefinition

import java.util.UUID
import java.net.URI

trait IngestPrep[T] {
  val self: T
  def decomposeTasks: Array[Ingest.Task]
}

