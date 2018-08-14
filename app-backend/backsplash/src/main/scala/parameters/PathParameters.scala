package com.azavea.rf.backsplash.parameters

import com.azavea.rf.backsplash.nodes.ProjectNode

import scala.util.Try

import java.util.UUID

object PathParameters {
  object UUIDWrapper {
    def unapply(s: String): Option[UUID] = {
      if(!s.isEmpty) Try(UUID.fromString(s)).toOption else None
    }
  }

  object ProjectNodeWrapper {
    def unapply(s: String): Option[ProjectNode] = {
      if (!s.isEmpty) Try(ProjectNode(UUID.fromString(s))).toOption else None
    }
  }
}
