package com.azavea.rf.backsplash.parameters

import com.azavea.rf.backsplash.nodes.ProjectNode

import scala.util.Try

import java.util.UUID

object PathParameters {
  object ProjectNodeWrapper {
    def unapply(s: String): Option[ProjectNode] = {
      if (!s.isEmpty) Try(ProjectNode(UUID.fromString(s))).toOption else None
    }
  }
}
