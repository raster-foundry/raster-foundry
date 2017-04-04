package com.azavea.rf.datamodel

import java.util.UUID

// --- //

/** Represents a many-to-many relationship between [[AOI]]s and
  * [[Project]]s.
  */
case class AoiToProject(aoiId: UUID, projectId: UUID)
