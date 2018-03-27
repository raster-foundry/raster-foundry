package com.azavea.rf.datamodel

import java.util.UUID

case class WorkspaceTag(workspaceId: UUID, TagId: UUID) {
}

object WorkspaceTag {
  case class WithRelated(workspadeId: UUID, tag: Tag) {
  }
}
