package com.azavea.rf.datamodel

import io.circe._

import java.util.UUID
import java.sql.Timestamp

import io.circe.generic.JsonCodec

@JsonCodec
case class Workspace(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  createdBy: String,
  modifiedBy: String,
  owner: String,
  organizationId: UUID,
  name: String,
  description: String,
  activeAnalysis: Option[UUID]
) {
  def withRelatedFromComponents(
    tags: List[Tag], categories: List[Category], analyses: List[Analysis]
  ): Workspace.WithRelated = Workspace.WithRelated(
    this.id,
    this.createdAt,
    this.modifiedAt,
    this.createdBy,
    this.modifiedBy,
    this.owner,
    this.organizationId,
    this.name,
    this.description,
    this.activeAnalysis,
    tags,
    categories,
    analyses
  )
}

object Workspace {
  def create = Create.apply _

  @JsonCodec
  case class Create(
    organizationId: UUID,
    owner: Option[String],
    name: String,
    description: String,
    tags: List[UUID],
    categories: List[String]
  ) extends OwnerCheck {
    def toWorkspace(user: User): (Workspace, List[WorkspaceTag], List[WorkspaceCategory]) = {
      val now = new Timestamp((new java.util.Date()).getTime())
      val workspaceId = UUID.randomUUID

      val ownerId = checkOwner(user, this.owner)
      val workspace = Workspace(
        workspaceId,
        now,
        now,
        user.id,
        user.id,
        ownerId,
        organizationId,
        name,
        description,
        None
      )

      val workspaceTags = tags.map(tagId => WorkspaceTag(workspaceId, tagId))
      val workspaceCategories = categories.map(categorySlug =>
        WorkspaceCategory(workspaceId, categorySlug))

      (workspace, workspaceTags, workspaceCategories)
    }
  }

  @JsonCodec
  case class WithRelated(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    owner: String,
    organization: UUID,
    name: String,
    description: String,
    activeAnalysis: Option[UUID],
    tags: List[Tag],
    categories: List[Category],
    analyses: List[Analysis]
  )

  @JsonCodec
  case class Update(
    id: UUID,
    owner: String,
    organizationId: UUID,
    name: String,
    description: String,
    activeAnalysis: Option[UUID],
    tags: List[UUID],
    categories: List[String]
  )
}
