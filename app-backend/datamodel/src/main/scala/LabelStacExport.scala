package com.rasterfoundry.datamodel

import java.sql.Timestamp
import java.util.{UUID, Date}

import io.circe.generic.JsonCodec

@JsonCodec
final case class LabelStacExport(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    modifiedBy: Option[String],
    owner: String,
    name: String,
    exportLocation: Option[String],
    exportStatus: ExportStatus,
    layerDefinition: List[LabelStacExport.LayerDefinition],
    isUnion: Boolean,
    taskStatuses: List[String])

object LabelStacExport {

  def tupled = (LabelStacExport.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  final case class LayerDefinition(projectId: UUID, layerId: UUID)

  @JsonCodec
  final case class Create(
      name: String,
      owner: Option[String],
      layerDefinition: List[LabelStacExport.LayerDefinition],
      isUnion: Boolean,
      taskStatuses: List[TaskStatus])
      extends OwnerCheck {
    def toLabelStacExport(user: User): LabelStacExport = {
      val id = UUID.randomUUID()
      val now = new Timestamp(new Date().getTime)
      val ownerId = checkOwner(user, this.owner)

      LabelStacExport(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        Some(user.id), // modifiedBy
        ownerId, // owner
        this.name,
        None,
        ExportStatus.NotExported,
        this.layerDefinition,
        this.isUnion,
        this.taskStatuses.map(_.toString)
      )
    }
  }
}
