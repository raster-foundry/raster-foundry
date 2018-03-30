package com.azavea.rf.datamodel

import io.circe._

import java.util.UUID
import java.sql.Timestamp

import io.circe.generic.JsonCodec

/** Model Lab Template */
@JsonCodec
case class Template(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  createdBy: String,
  modifiedBy: String,
  owner: String,
  organizationId: UUID,
  name: String,
  description: String,
  details: String,
  requirements: String,
  license: Option[String],
  visibility: Visibility,
  compatibleDataSources: List[String] = List.empty
) {
  def withRelatedFromComponents(
    tags: List[Tag], categories: List[Category], latestVersion: Option[TemplateVersion.WithRelated]
  ): Template.WithRelated = Template.WithRelated(
    this.id,
    this.createdAt,
    this.modifiedAt,
    this.createdBy,
    this.modifiedBy,
    this.owner,
    this.organizationId,
    this.name,
    this.description,
    this.details,
    this.requirements,
    this.license,
    this.visibility,
    this.compatibleDataSources,
    tags,
    categories,
    latestVersion
  )
}

/** Case class for template creation */
object Template {
  def create = Create.apply _

  def tupled = (Template.apply _).tupled

  @JsonCodec
  case class Create(
    organizationId: UUID,
    name: String,
    description: String,
    details: String,
    requirements: String,
    license: Option[String],
    visibility: Visibility,
    compatibleDataSources: List[String],
    owner: Option[String],
    tags: List[UUID],
    categories: List[String]
  ) extends OwnerCheck

  /** Template class when posted with category and tag ids */
  @JsonCodec
  case class WithRelated(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    owner: String,
    organizationId: UUID,
    name: String,
    description: String,
    details: String,
    requirements: String,
    license: Option[String],
    visibility: Visibility,
    compatibleDataSources: List[String] = List.empty,
    tags: List[Tag],
    categories: List[Category],
    latestVersion: Option[TemplateVersion.WithRelated]
  )

  @JsonCodec
  case class WithRelatedUUIDs(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    owner: String,
    organizationId: UUID,
    name: String,
    description: String,
    details: String,
    requirements: String,
    license: Option[String],
    visibility: Visibility,
    compatibleDataSources: List[String] = List.empty,
    tags: List[UUID],
    categories: List[String],
    latestVersion: Option[UUID]
  )
}
