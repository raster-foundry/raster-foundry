package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

/** Represents a many-to-many relatioØnship between [[AOI]]s and
  * [[Project]]s.
  */
final case class AoiToProject(aoiId: UUID,
                              projectId: UUID,
                              approvalRequired: Boolean,
                              startTime: Timestamp)
