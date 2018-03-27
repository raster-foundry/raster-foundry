package com.azavea.rf.datamodel

import io.circe._

import java.util.UUID
import java.sql.Timestamp

import io.circe.generic.JsonCodec

/** Model Lab TemplateVersion */
@JsonCodec
case class TemplateVersion(
  id: Long,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  createdBy: String,
  modifiedBy: String,
  version: String,
  description: String,
  changelog: String,
  templateId: UUID,
  analysisId: UUID
) {
  def withRelatedFromComponents(analysis: Analysis) = TemplateVersion.WithRelated(
    this.id,
    this.createdAt,
    this.modifiedAt,
    this.createdBy,
    this.modifiedBy,
    this.version,
    this.description,
    this.changelog,
    this.templateId,
    analysis
  )
}

object TemplateVersion {
  @JsonCodec
  case class Create(
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    version: String,
    description: String,
    changelog: String,
    templateId: UUID,
    analysisId: UUID
  )

  @JsonCodec
  case class CreateWithRelated(
    version: String,
    description: String,
    changelog: String,
    templateId: UUID,
    analysis: Analysis.Create
  ) extends OwnerCheck {
    def toCreate(
      user: User, analysis: Analysis
    ): TemplateVersion.Create = {
      val now = new Timestamp((new java.util.Date()).getTime())

      TemplateVersion.Create(
        now, // createdAt
        now, // modifiedAt
        user.id, // createdBy
        user.id, // modifiedBy
        version,
        description,
        changelog,
        templateId,
        analysis.id
      )
    }
  }

  @JsonCodec
  case class WithRelated(
    id: Long,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    version: String,
    description: String,
    changelog: String,
    templateId: UUID,
    analysis: Analysis
  )
}
