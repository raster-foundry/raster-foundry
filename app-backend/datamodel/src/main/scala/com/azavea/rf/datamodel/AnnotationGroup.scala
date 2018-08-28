package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
final case class AnnotationGroup(id: UUID,
                                 name: String,
                                 createdAt: Timestamp,
                                 createdBy: String,
                                 modifiedAt: Timestamp,
                                 modifiedBy: String,
                                 projectId: UUID,
                                 defaultStyle: Option[Json])

object AnnotationGroup {
  @JsonCodec
  final case class Create(name: String, defaultStyle: Option[Json]) {
    def toAnnotationGroup(projectId: UUID, user: User): AnnotationGroup = {
      val now = new Timestamp(new java.util.Date().getTime)
      AnnotationGroup(
        UUID.randomUUID,
        name,
        now,
        user.id,
        now,
        user.id,
        projectId,
        defaultStyle
      )
    }
  }
}
