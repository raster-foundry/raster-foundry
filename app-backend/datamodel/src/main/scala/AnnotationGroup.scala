package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.JsonCodec

import java.sql.Timestamp
import java.util.UUID

@JsonCodec
final case class LabelSummary(label: String, counts: Json)

@JsonCodec
final case class AnnotationGroup(
    id: UUID,
    name: String,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    projectId: UUID,
    defaultStyle: Option[Json],
    projectLayerId: UUID
)

object AnnotationGroup {
  @JsonCodec
  final case class Create(name: String, defaultStyle: Option[Json]) {
    def toAnnotationGroup(
        projectId: UUID,
        user: User,
        projectLayerId: UUID
    ): AnnotationGroup = {
      val now = new Timestamp(new java.util.Date().getTime)
      AnnotationGroup(
        UUID.randomUUID,
        name,
        now,
        user.id,
        now,
        projectId,
        defaultStyle,
        projectLayerId
      )
    }
  }
}
